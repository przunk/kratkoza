package com.przunk.kratkoza.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.BoardInsights
import com.przunk.kratkoza.domain.game.GameEngine
import com.przunk.kratkoza.domain.game.GameState
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives one game: the board, the clock, the pause, and the automatic save.
 *
 * The board rules live in [GameEngine]; this class decides *when* they run and what gets persisted.
 * That split is what technical_requirements.md sections 4 and 5 ask for, and it is why the rules can
 * be tested without an Android runtime.
 */
class GameViewModel(
    private val puzzleId: String,
    private val gameRepository: GameRepository,
    /**
     * The settings stream, not the repository that owns it. The screen only ever reads settings, so
     * taking the flow keeps this class free of Android `Context` and testable on the JVM.
     */
    settingsFlow: Flow<GameSettings>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var game: SavedGame? = null
    private var settings: GameSettings = GameSettings.DEFAULT

    /** The cell the player has tapped, or `null` when nothing is selected. */
    private var selection: Int? = null

    /** Rises with every move, so the board can tell a fresh move from a redraw. */
    private var moveToken: Int = 0
    private var lastMove: GameUiState.MoveEcho? = null

    /** When the running clock last started, or `null` while paused. */
    private var runningSince: Long? = null

    /** Play time banked before the current run; the clock is the sum of this and the current run. */
    private var bankedMillis: Long = 0

    private var ticker: Job? = null

    init {
        settingsFlow
            .onEach { updated ->
                settings = updated
                // Only the parts of the state that depend on settings change here; a settings edit
                // must never look like a move.
                game?.let { render(it.state) }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = gameRepository.loadGame(puzzleId)
        if (loaded == null) {
            // Gone, or too damaged to restore. Staying in LOADING would spin forever, so the screen
            // is told to leave instead.
            _uiState.value = GameUiState(phase = GameUiState.Phase.MISSING)
            return
        }

        game = loaded
        bankedMillis = loaded.elapsedMillis
        // The clock starts before the first render, not after it. `isPaused` is derived from
        // whether the clock is running, so rendering first would raise the pause cover over a game
        // that is in fact running, and nothing would take it down until the player touched
        // something.
        if (!loaded.state.isSolved) resumeClock()
        render(loaded.state)
    }

    fun selectCell(index: Int) {
        if (!_uiState.value.isPlayable) return
        require(Sudoku.isCellIndex(index)) { "Cell index out of range: $index" }
        val state = game?.state ?: return
        // Tapping the selected cell again clears the selection, which is the only way to get the
        // highlights out of the way without picking a different cell.
        selection = if (selection == index) null else index
        render(state)
    }

    fun enterDigit(digit: Int) {
        val state = game?.state ?: return
        val index = selection ?: return
        if (!_uiState.value.isPlayable) return

        val candidateMode = _uiState.value.candidateMode
        // A completed digit cannot be entered again, but an old pencil mark must remain removable
        // even after its digit becomes complete. Candidate availability distinguishes those cases.
        if (!candidateMode && state.isDigitComplete(digit)) return

        val updated = if (candidateMode) {
            if (digit !in _uiState.value.availableCandidateDigits) return
            GameEngine.toggleCandidate(state, index, digit)
        } else {
            GameEngine.enterDigit(state, index, digit, settings.autoRemoveCandidates)
        }
        apply(updated)
    }

    fun erase() {
        val state = game?.state ?: return
        val index = selection ?: return
        if (!_uiState.value.isPlayable) return
        apply(GameEngine.clearCell(state, index))
    }

    fun undo() {
        val state = game?.state ?: return
        if (!_uiState.value.isPlayable) return
        apply(GameEngine.undo(state))
    }

    fun toggleCandidateMode() {
        if (!_uiState.value.isPlayable) return
        _uiState.value = _uiState.value.copy(candidateMode = !_uiState.value.candidateMode)
    }

    /** The pause button (project.md section 20). */
    fun setPaused(paused: Boolean) {
        if (_uiState.value.phase == GameUiState.Phase.SOLVED) return
        if (paused == _uiState.value.isPaused) return
        if (paused) pauseClock() else resumeClock()
        game?.let { render(it.state) }
    }

    /**
     * The Save button: banks the clock and writes the game out, then the screen leaves.
     *
     * project.md section 21 already has the game saved after every move, so this adds no durability
     * the player did not have. What it adds is the clock — which is only banked when it stops — and
     * a visible answer to "can I leave without losing this?". A guarantee nobody can see is a
     * guarantee nobody trusts.
     */
    fun saveAndStop() = onLeftForeground()

    /**
     * Called when the app goes to the background (project.md section 20: minimising pauses the
     * game). Also the last chance to write the clock out before the process may be killed.
     */
    fun onLeftForeground() {
        if (_uiState.value.phase == GameUiState.Phase.SOLVED) return
        pauseClock()
        game?.let { render(it.state) }
        persist()
    }

    /** Applies a move: renders it, banks the clock and writes the save (project.md section 21). */
    private fun apply(state: GameState) {
        val current = game ?: return
        if (state === current.state) return

        moveToken++
        lastMove = echoOf(before = current.state, after = state)

        game = current.copy(state = state)
        render(state)

        if (state.isSolved) {
            pauseClock()
            viewModelScope.launch { finish() }
        } else {
            persist()
        }
    }

    /**
     * What the board should show for a move that has just been applied.
     *
     * A mistake takes precedence and lights the cell's whole row, column and block — the point is
     * to show *where* the wrong digit now sits, not merely that it is wrong, which the cell itself
     * already says. project.md section 16 counts a mistake on the digit being committed, so the
     * counter rising is exactly the signal.
     */
    private fun echoOf(before: GameState, after: GameState): GameUiState.MoveEcho {
        val cell = selection
        val isMistake = after.mistakeCount > before.mistakeCount

        if (isMistake && cell != null) {
            return GameUiState.MoveEcho(
                token = moveToken,
                cell = cell,
                flashCells = Sudoku.unitsOf[cell].flatMapTo(mutableSetOf(), IntArray::asList),
                flashKind = GameUiState.FlashKind.MISTAKE,
            )
        }

        val completed = BoardInsights.newlyCompletedCells(
            before = before.board,
            after = after.board,
            solution = after.puzzle.solution,
        )
        return GameUiState.MoveEcho(
            token = moveToken,
            cell = cell,
            flashCells = completed,
            flashKind = if (completed.isEmpty()) {
                GameUiState.FlashKind.NONE
            } else {
                GameUiState.FlashKind.COMPLETED
            },
        )
    }

    private suspend fun finish() {
        val finished = snapshot() ?: return
        gameRepository.finishGame(finished, completed = true)
    }

    /** Writes the current game out. Fire and forget: the next move will write again anyway. */
    private fun persist() {
        val snapshot = snapshot() ?: return
        viewModelScope.launch { gameRepository.save(snapshot) }
    }

    /** The game with the clock read at this instant, ready to be stored. */
    private fun snapshot(): SavedGame? = game?.copy(
        elapsedMillis = elapsedMillis(),
        lastPlayedAt = clock(),
    )

    private fun elapsedMillis(): Long =
        bankedMillis + (runningSince?.let { clock() - it } ?: 0)

    private fun resumeClock() {
        if (runningSince != null) return
        runningSince = clock()
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                _uiState.value = _uiState.value.copy(elapsedMillis = elapsedMillis())
            }
        }
    }

    private fun pauseClock() {
        val startedAt = runningSince ?: return
        bankedMillis += clock() - startedAt
        runningSince = null
        ticker?.cancel()
        ticker = null
    }

    /** Rebuilds the UI state from a board state. The single place UI state is produced. */
    private fun render(state: GameState) {
        val selected = selection
        val selectedDigit = selected?.let { state.board[it] } ?: Sudoku.EMPTY

        _uiState.value = GameUiState(
            phase = if (state.isSolved) GameUiState.Phase.SOLVED else GameUiState.Phase.PLAYING,
            board = state.board,
            givens = state.puzzle.givens,
            candidates = state.candidates,
            incorrectCells = state.incorrectCells,
            selectedCell = selected,
            peerCells = selected?.let { Sudoku.peers[it].toSet() } ?: emptySet(),
            sameDigitCells = when {
                !settings.highlightSameDigits -> emptySet()
                selectedDigit == Sudoku.EMPTY -> emptySet()
                else -> (0 until Sudoku.CELL_COUNT).filterTo(mutableSetOf()) {
                    state.board[it] == selectedDigit
                }
            },
            difficulty = state.puzzle.difficulty,
            mistakeCount = state.mistakeCount,
            elapsedMillis = elapsedMillis(),
            isPaused = runningSince == null && !state.isSolved,
            candidateMode = _uiState.value.candidateMode,
            availableCandidateDigits = selected?.let { cell ->
                SudokuValidator.candidatesFor(state.board, cell) + state.candidates[cell]
            }.orEmpty(),
            canUndo = state.canUndo,
            remainingByDigit = List(10) { digit ->
                if (digit == 0) 0 else state.remainingCountOf(digit)
            },
            showRemainingCounts = settings.showRemainingCounts,
            eightOfNineHints = if (settings.showEightOfNineHint) {
                BoardInsights.eightOfNineHints(state.board, state.puzzle.solution)
            } else {
                emptyMap()
            },
            lastMove = lastMove,
        )
    }

    override fun onCleared() {
        pauseClock()
    }

    private companion object {
        /**
         * Four ticks a second. The display only shows seconds, but a slower tick makes the seconds
         * visibly jitter against the wall clock.
         */
        const val TICK_MILLIS = 250L
    }
}
