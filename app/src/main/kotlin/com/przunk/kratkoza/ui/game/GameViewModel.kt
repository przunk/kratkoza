package com.przunk.kratkoza.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.BoardInsights
import com.przunk.kratkoza.BuildConfig
import com.przunk.kratkoza.domain.game.FinishedGame
import com.przunk.kratkoza.domain.game.GameEngine
import com.przunk.kratkoza.domain.game.GameState
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.data.ranking.RankingSubmitter
import com.przunk.kratkoza.domain.ranking.RankingLog
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.share.BoardCode
import com.przunk.kratkoza.domain.sudoku.Sudoku
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    /**
     * Where a finished Daily goes afterwards. Defaults to nowhere so that every test, and every
     * build without Play Games configured, behaves like a player who is simply not signed in.
     */
    /** Absent in tests that do not care; the ranking is not this screen's job beyond handing over. */
    private val rankingSubmitter: RankingSubmitter? = null,
    /**
     * Whether to offer the debug-only shortcuts. Defaults to the build type, so the control cannot
     * reach a release APK even if somebody forgets to hide it in the UI.
     */
    private val debugTools: Boolean = BuildConfig.DEBUG,
    /** Where ranking events are kept so they can be read after the moment has passed. */
    private val rankingLog: RankingLog = RankingLog(),
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

        // project.md section 19: once all nine are correctly placed the digit can go nowhere
        // else, so pressing it could only ever be a mistake. Guarded here as well as on the pad —
        // a rule that lives only in a button is a rule the next caller does not have.
        if (state.isDigitComplete(digit)) return

        val updated = if (_uiState.value.candidateMode) {
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

    /**
     * Reveals the selected cell without creating a player move (project.md section 18).
     *
     * The UI omits this action from a publicly ranked game, and the same rule is enforced here so
     * a future caller cannot bypass it. Every reachable reveal therefore starts and remains
     * non-ranked; no score or mistake penalty is introduced.
     */
    fun revealDigit() {
        val current = game ?: return
        val index = selection ?: return
        if (!_uiState.value.canRevealDigit || current.rankedEligible) return

        apply(GameEngine.revealDigit(current.state, index, settings.autoRemoveCandidates))
    }

    /**
     * Debug builds only: finishes the board as though every remaining digit had been tapped in.
     *
     * It really does tap them in — same selection, same entry, same completion check — rather than
     * writing a solved board straight to the database. A shortcut that bypassed the normal path
     * would prove nothing about the path being tested, and the reason this exists is to reach the
     * end of a Daily without spending twenty minutes on it.
     */
    fun autoSolve() {
        if (!debugTools) return
        val solution = game?.state?.puzzle?.solution ?: return

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (game?.state?.board?.isEmpty(cell) != true) continue
            selectCell(cell)
            enterDigit(solution[cell])
        }
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

    /**
     * Takes a game out of the ranking for good, banking the time it had run up.
     *
     * Kept for actions whose contract permits forfeiting an active ranking. A reveal deliberately
     * does not call it: project.md section 18 forbids revealing while the game still counts rather
     * than offering the reveal as a way to opt out.
     */
    private fun loseRanking() {
        val current = game?.takeIf { it.rankedEligible } ?: return
        bankedMillis = elapsedMillis()
        runningSince = clock()
        game = current.copy(rankedEligible = false)
    }

    /** The pause button (project.md section 20). */
    fun setPaused(paused: Boolean) {
        if (_uiState.value.phase == GameUiState.Phase.SOLVED) return
        // project.md section 28: a game that still counts for the ranking has no pause. Refused
        // here rather than only hidden in the UI, so no other caller can reach around it.
        if (paused && game?.rankedEligible == true) return
        if (paused == _uiState.value.isPaused) return
        if (paused) pauseClock() else resumeClock()
        game?.let { render(it.state) }
    }

    /**
     * The code that hands this board to somebody else.
     *
     * `null` until the board is loaded — there is nothing to share before then, and the button is
     * hidden rather than handed an empty string to send.
     */
    fun shareCode(): String? = game?.state?.puzzle?.let {
        BoardCode.encode(it.givens, it.generatorVersion)
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

        // A ranked game keeps running. project.md section 28 has minimising cost the Daily its
        // place in the ranking; the rule was relaxed on 2026-08-26 because the clock already does
        // that job — see docs/OPEN_QUESTIONS.md. What the section is really protecting against is
        // free thinking time, and time that keeps running is not free. Losing the ranking outright
        // punished answering a notification with total exclusion, which section 31's
        // "competitive-casual" does not ask for.
        if (game?.rankedEligible != true) pauseClock()

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
        val snapshot = snapshot() ?: run {
            rankingLog.add("game: finished with no snapshot")
            return
        }
        val finished = gameRepository.finishGame(snapshot, completed = true)
        rankingLog.add("game: finished ${finished.gameType} ${finished.difficulty} score=${finished.score}")

        // Handed over rather than awaited. The submitter runs on the application's scope, so the
        // sequence survives this screen being left — which it previously did not.
        rankingSubmitter?.submit(finished, puzzleId)
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

    /**
     * How long this game has been going.
     *
     * A ranked game is measured from when it was opened, against the wall clock, because it cannot
     * be paused and therefore has nothing to subtract. That also makes it survive the process being
     * killed: an in-app ticker stops when the app does, so a player who closed the app for an hour
     * would come back to an hour of free thinking. Wall-clock arithmetic cannot be cheated that way.
     *
     * Everything else accumulates the usual way, banking time whenever the clock stops.
     */
    private fun elapsedMillis(): Long {
        val current = game
        if (current != null && current.rankedEligible) {
            return (clock() - current.startedAt).coerceAtLeast(0)
        }
        return bankedMillis + (runningSince?.let { clock() - it } ?: 0)
    }

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
            gameType = state.puzzle.gameType,
            mistakeCount = state.mistakeCount,
            debugTools = debugTools,
            elapsedMillis = elapsedMillis(),
            isPaused = runningSince == null && !state.isSolved,
            candidateMode = _uiState.value.candidateMode,
            canUndo = state.canUndo,
            remainingByDigit = List(10) { digit ->
                if (digit == 0) 0 else state.remainingCountOf(digit)
            },
            showRemainingCounts = settings.showRemainingCounts,
            rankedEligible = game?.rankedEligible == true,
            // project.md section 17: the hint is unavailable in a publicly ranked Daily. Checked
            // against eligibility rather than against the game type, so a Daily that has dropped
            // out of the ranking gets its help back — at that point it is an ordinary game, and
            // there is nothing left to protect.
            eightOfNineHints = if (settings.showEightOfNineHint && game?.rankedEligible != true) {
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
