package com.przunk.kratkoza.ui.game

import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
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
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private val settings = MutableStateFlow(GameSettings.DEFAULT)
    private var now = 0L

    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
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
        val wrong = (1..9).first { it != game.state.puzzle.solution[cell] }

        viewModel.selectCell(cell)
        viewModel.enterDigit(wrong)
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

    @Test
    fun `the save button banks the clock and writes the game out`() = gameTest {
        // project.md section 21 already saves after every move; what Save adds is the clock, which
        // is only banked when it stops.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }

        viewModel.selectCell(cell)
        viewModel.enterDigit(game.state.puzzle.solution[cell])
        now = 45_000
        viewModel.saveAndStop()
        idle()

        val stored = requireNotNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(45_000L, stored.elapsedMillis)
        assertEquals(game.state.puzzle.solution[cell], stored.state.board[cell])
        assertTrue(viewModel.uiState.value.isPaused)
    }

    @Test
    fun `the hint appears only while the option is on`() = gameTest {
        // project.md section 17, off by default.
        val game = startGame()
        val viewModel = viewModelFor(game)
        assertTrue(viewModel.uiState.value.eightOfNineHints.isEmpty())

        settings.value = GameSettings.DEFAULT.copy(showEightOfNineHint = true)
        idle()

        // Fill a row bar one cell, so exactly one gap is left for the hint to name.
        val row = Sudoku.rows[0]
        val gap = row.last { game.state.board.isEmpty(it) }
        for (cell in row) {
            if (cell == gap || !game.state.board.isEmpty(cell)) continue
            viewModel.selectCell(cell)
            viewModel.enterDigit(game.state.puzzle.solution[cell])
        }

        assertEquals(game.state.puzzle.solution[gap], viewModel.uiState.value.eightOfNineHints[gap])
    }

    @Test
    fun `every move carries a fresh token so an animation is not replayed`() = gameTest {
        // project.md section 36. The board keys its animations on the token; a redraw caused by the
        // clock or by a settings change must not look like another move.
        val game = startGame()
        val viewModel = viewModelFor(game)
        assertNull(viewModel.uiState.value.lastMove)

        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val wrong = (1..9).first { it != game.state.puzzle.solution[cell] }
        viewModel.selectCell(cell)
        viewModel.enterDigit(wrong)
        val first = requireNotNull(viewModel.uiState.value.lastMove)
        assertEquals(cell, first.cell)

        settings.value = GameSettings.DEFAULT.copy(showRemainingCounts = false)
        idle()
        assertEquals("a settings change is not a move", first.token, viewModel.uiState.value.lastMove?.token)

        viewModel.erase()
        assertTrue(viewModel.uiState.value.lastMove!!.token > first.token)
    }

    @Test
    fun `finishing a row reports its cells for the celebration`() = gameTest {
        val game = startGame()
        val viewModel = viewModelFor(game)
        val row = Sudoku.rows[0]

        for (cell in row) {
            if (!game.state.board.isEmpty(cell)) continue
            viewModel.selectCell(cell)
            viewModel.enterDigit(game.state.puzzle.solution[cell])
        }

        val echo = viewModel.uiState.value.lastMove!!
        assertEquals(GameUiState.FlashKind.COMPLETED, echo.flashKind)
        assertTrue("the finished row must be reported", row.all { it in echo.flashCells })
    }

    @Test
    fun `a wrong digit flashes its row, column and block`() = gameTest {
        // project.md section 36. The cell already says it is wrong; the flash says where it sits.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }

        viewModel.selectCell(cell)
        viewModel.enterDigit(GameFixturesFor(game).wrongDigitAt(cell))

        val echo = viewModel.uiState.value.lastMove!!
        assertEquals(GameUiState.FlashKind.MISTAKE, echo.flashKind)
        assertEquals(21, echo.flashCells.size)
        assertTrue(Sudoku.peers[cell].all { it in echo.flashCells })
        assertTrue(cell in echo.flashCells)
    }

    @Test
    fun `a correct digit that finishes nothing flashes nothing`() = gameTest {
        val game = startGame()
        val viewModel = viewModelFor(game)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }

        viewModel.selectCell(cell)
        viewModel.enterDigit(game.state.puzzle.solution[cell])

        val echo = viewModel.uiState.value.lastMove!!
        assertEquals(GameUiState.FlashKind.NONE, echo.flashKind)
        assertTrue(echo.flashCells.isEmpty())
    }

    @Test
    fun `a digit with all nine placed is refused`() = gameTest {
        // The pad dims the key, but the rule is enforced here too: a rule that lives only in a
        // button is a rule the next caller does not have.
        val game = startGame()
        val viewModel = viewModelFor(game)
        val digit = 5

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (game.state.board.isEmpty(cell) && game.state.puzzle.solution[cell] == digit) {
                viewModel.selectCell(cell)
                viewModel.enterDigit(digit)
            }
        }
        assertEquals(0, viewModel.uiState.value.remainingByDigit[digit])

        val settled = viewModel.uiState.value
        val elsewhere = (0 until Sudoku.CELL_COUNT).first { settled.board.isEmpty(it) }
        viewModel.selectCell(elsewhere)
        viewModel.enterDigit(digit)

        assertTrue("the finished digit must not land", settled.board[elsewhere] == 0)
        assertEquals(0, viewModel.uiState.value.mistakeCount)
    }

    @Test
    fun `a daily that still counts cannot be paused`() = gameTest {
        // project.md section 28.
        val day = java.time.LocalDate.parse("2026-08-26")
        repository.dailySet(day)
        val daily = repository.startDaily(day, com.przunk.kratkoza.domain.model.Difficulty.EASY)
        val viewModel = viewModelFor(daily)

        assertTrue(viewModel.uiState.value.rankedEligible)
        assertFalse("no pause button while it counts", viewModel.uiState.value.canPause)

        now = 5_000
        viewModel.setPaused(true)
        assertFalse("the request must be refused, not merely hidden", viewModel.uiState.value.isPaused)
    }

    @Test
    fun `going to the background costs a daily its ranking and gives back the pause`() = gameTest {
        val day = java.time.LocalDate.parse("2026-08-26")
        repository.dailySet(day)
        val daily = repository.startDaily(day, com.przunk.kratkoza.domain.model.Difficulty.HARD)
        val viewModel = viewModelFor(daily)

        now = 20_000
        viewModel.onLeftForeground()
        idle()

        assertFalse("project.md section 28", viewModel.uiState.value.rankedEligible)
        assertTrue("and it becomes an ordinary game, so it may pause", viewModel.uiState.value.canPause)
        assertEquals(
            "the loss must survive a restart",
            false,
            repository.loadGame(daily.state.puzzle.id)?.rankedEligible,
        )
    }

    @Test
    fun `a solo game may always be paused`() = gameTest {
        val viewModel = viewModelFor(startGame())

        assertFalse(viewModel.uiState.value.rankedEligible)
        assertTrue(viewModel.uiState.value.canPause)
        viewModel.setPaused(true)
        assertTrue(viewModel.uiState.value.isPaused)
    }
}

/** Picks a digit the solution disagrees with, for a board the generator produced. */
private class GameFixturesFor(private val game: com.przunk.kratkoza.domain.game.SavedGame) {
    fun wrongDigitAt(cell: Int): Int = (1..9).first { it != game.state.puzzle.solution[cell] }
}
