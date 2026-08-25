package com.przunk.kratkoza.ui.game

import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The ViewModel's own responsibilities: what a tap does, when the clock runs, and when the game is
 * written out. The board rules themselves are covered by `GameEngineTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao()
    private val settings = MutableStateFlow(GameSettings.DEFAULT)
    private var now = 0L

    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = dispatcher,
        clock = { now },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun startGame(difficulty: Difficulty = Difficulty.NORMAL): SavedGame =
        repository.startNewGame(difficulty)

    /**
     * ViewModels are put in a store so the test can end them the way the framework does.
     *
     * The clock is an endless `while (isActive) delay(...)` loop on the main dispatcher. `runTest`
     * refuses to finish while such a coroutine is still pending, so every test has to clear the
     * store before it returns — which is what [gameTest] does.
     */
    private val store = ViewModelStore()

    private fun viewModelFor(game: SavedGame): GameViewModel {
        val viewModel = GameViewModel(
            puzzleId = game.state.puzzle.id,
            gameRepository = repository,
            settingsFlow = settings,
            clock = { now },
        )
        store.put(game.state.puzzle.id, viewModel)
        idle()
        return viewModel
    }

    /**
     * Runs the coroutines queued so far — loading the board, writing a save — without advancing the
     * virtual clock.
     *
     * `advanceUntilIdle` must not be used here: the game clock is an endless `delay` loop, so
     * advancing virtual time until nothing is pending never returns.
     */
    private fun idle() = dispatcher.scheduler.runCurrent()

    private fun gameTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            store.clear()
        }
    }

    @Test
    fun `the board loads and the clock starts`() = gameTest {
        val game = startGame()
        val viewModel = viewModelFor(game)

        val state = viewModel.uiState.value
        assertEquals(GameUiState.Phase.PLAYING, state.phase)
        assertEquals(game.state.puzzle.givens, state.board)
        assertEquals(Difficulty.NORMAL, state.difficulty)
        assertFalse(state.isPaused)
    }

    @Test
    fun `a missing save tells the screen to leave`() = gameTest {
        val viewModel = GameViewModel(
            puzzleId = "no-such-puzzle",
            gameRepository = repository,
            settingsFlow = settings,
            clock = { now },
        )
        store.put("no-such-puzzle", viewModel)
        idle()
        assertEquals(GameUiState.Phase.MISSING, viewModel.uiState.value.phase)
    }

    @Test
    fun `selecting a cell highlights its row, column and block`() = gameTest {
        // project.md section 8.
        val viewModel = viewModelFor(startGame())
        val cell = Sudoku.index(row = 4, column = 4)
        viewModel.selectCell(cell)

        val state = viewModel.uiState.value
        assertEquals(cell, state.selectedCell)
        assertEquals(Sudoku.peers[cell].toSet(), state.peerCells)
        assertFalse("the selected cell is not its own peer", cell in state.peerCells)
        assertEquals(20, state.peerCells.size)
    }

    @Test
    fun `tapping the selected cell again clears the selection`() = gameTest {
        val viewModel = viewModelFor(startGame())
        viewModel.selectCell(0)
        viewModel.selectCell(0)

        assertNull(viewModel.uiState.value.selectedCell)
        assertTrue(viewModel.uiState.value.peerCells.isEmpty())
    }

    @Test
    fun `matching digits are highlighted only while the option is on`() = gameTest {
        // project.md section 10.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val given = (0 until Sudoku.CELL_COUNT).first { !game.state.puzzle.givens.isEmpty(it) }
        val digit = game.state.puzzle.givens[given]

        viewModel.selectCell(given)
        val highlighted = viewModel.uiState.value.sameDigitCells
        assertTrue(highlighted.isNotEmpty())
        assertTrue(highlighted.all { game.state.board[it] == digit })

        settings.value = GameSettings.DEFAULT.copy(highlightSameDigits = false)
        idle()
        assertTrue(viewModel.uiState.value.sameDigitCells.isEmpty())
    }

    @Test
    fun `entering a digit writes it and saves the game`() = gameTest {
        // project.md section 21: the state is written after every meaningful move.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val writesBefore = savedGameDao.writeCount

        viewModel.selectCell(cell)
        viewModel.enterDigit(game.state.puzzle.solution[cell])
        idle()

        assertEquals(game.state.puzzle.solution[cell], viewModel.uiState.value.board[cell])
        assertTrue(savedGameDao.writeCount > writesBefore)
        assertEquals(
            game.state.puzzle.solution[cell],
            repository.loadGame(game.state.puzzle.id)?.state?.board?.get(cell),
        )
    }

    @Test
    fun `a digit with nothing selected does nothing`() = gameTest {
        val game = startGame()
        val viewModel = viewModelFor(game)
        viewModel.enterDigit(5)

        assertEquals(game.state.puzzle.givens, viewModel.uiState.value.board)
    }

    @Test
    fun `candidate mode writes pencil marks instead of digits`() = gameTest {
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }

        viewModel.selectCell(cell)
        viewModel.toggleCandidateMode()
        viewModel.enterDigit(4)

        val state = viewModel.uiState.value
        assertTrue(state.candidateMode)
        assertTrue(state.board.isEmpty(cell))
        assertEquals(setOf(4), state.candidates[cell])
    }

    @Test
    fun `a wrong digit is counted and marked`() = gameTest {
        // project.md section 16.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val wrong = (1..9).first { it != game.state.puzzle.solution[cell] }

        viewModel.selectCell(cell)
        viewModel.enterDigit(wrong)

        assertEquals(1, viewModel.uiState.value.mistakeCount)
        assertEquals(setOf(cell), viewModel.uiState.value.incorrectCells)
    }

    @Test
    fun `undo restores the board but not the mistake`() = gameTest {
        // project.md section 12.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val wrong = (1..9).first { it != game.state.puzzle.solution[cell] }

        viewModel.selectCell(cell)
        viewModel.enterDigit(wrong)
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.undo()
        assertTrue(viewModel.uiState.value.board.isEmpty(cell))
        assertEquals(1, viewModel.uiState.value.mistakeCount)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun `erase empties the selected cell`() = gameTest {
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }

        viewModel.selectCell(cell)
        viewModel.enterDigit(game.state.puzzle.solution[cell])
        viewModel.erase()

        assertTrue(viewModel.uiState.value.board.isEmpty(cell))
    }

    @Test
    fun `pausing stops the clock and nothing can be played`() = gameTest {
        // project.md section 20.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }

        now = 5_000
        viewModel.setPaused(true)
        val frozen = viewModel.uiState.value.elapsedMillis
        assertTrue(viewModel.uiState.value.isPaused)
        assertFalse(viewModel.uiState.value.isPlayable)

        now = 60_000
        viewModel.selectCell(cell)
        viewModel.enterDigit(1)
        assertEquals(frozen, viewModel.uiState.value.elapsedMillis)
        assertNull(viewModel.uiState.value.selectedCell)
        assertTrue(viewModel.uiState.value.board.isEmpty(cell))

        // Time spent paused is not play time: resuming picks up from where it stopped.
        viewModel.setPaused(false)
        now = 65_000
        viewModel.selectCell(cell)
        assertEquals(frozen + 5_000, viewModel.uiState.value.elapsedMillis)
    }

    @Test
    fun `going to the background pauses the game and writes it out`() = gameTest {
        // project.md sections 20 and 21.
        val game = startGame()
        val viewModel = viewModelFor(game)

        now = 30_000
        viewModel.onLeftForeground()
        idle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(30_000L, repository.loadGame(game.state.puzzle.id)?.elapsedMillis)
    }

    @Test
    fun `solving the board finishes the game`() = gameTest {
        // project.md section 16: only a correctly solved board counts as finished.
        val game = startGame(Difficulty.EASY)
        val viewModel = viewModelFor(game)

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (!game.state.puzzle.givens.isEmpty(cell)) continue
            viewModel.selectCell(cell)
            viewModel.enterDigit(game.state.puzzle.solution[cell])
        }
        idle()

        assertEquals(GameUiState.Phase.SOLVED, viewModel.uiState.value.phase)
        assertEquals(0, viewModel.uiState.value.mistakeCount)

        // The history row is closed as completed and the save is gone.
        assertTrue(historyDao.all.single().completed)
        assertNull(repository.loadGame(game.state.puzzle.id))
    }

    @Test
    fun `a resumed game carries on from the time it had`() = gameTest {
        val game = startGame()
        repository.save(game.copy(elapsedMillis = 120_000))

        now = 500_000
        val viewModel = viewModelFor(game)
        assertEquals(120_000L, viewModel.uiState.value.elapsedMillis)

        now = 505_000
        viewModel.selectCell(0)
        assertEquals(125_000L, viewModel.uiState.value.elapsedMillis)
    }

    @Test
    fun `remaining counts follow the board`() = gameTest {
        // project.md section 19.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val digit = game.state.puzzle.solution[cell]
        val before = viewModel.uiState.value.remainingByDigit[digit]

        viewModel.selectCell(cell)
        viewModel.enterDigit(digit)

        assertEquals(before - 1, viewModel.uiState.value.remainingByDigit[digit])
    }
}
