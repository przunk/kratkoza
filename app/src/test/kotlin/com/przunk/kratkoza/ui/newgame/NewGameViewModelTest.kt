package com.przunk.kratkoza.ui.newgame

import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import com.przunk.kratkoza.domain.share.BoardCode
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewGameViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repositoryWith(generator: SudokuGenerator) = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = generator,
        computationDispatcher = dispatcher,
    )

    @Test
    fun `a generated game is announced for navigation`() = runTest(dispatcher) {
        val viewModel = NewGameViewModel(repositoryWith(SudokuGenerator()))
        viewModel.startGame(Difficulty.EASY)

        val opened = viewModel.createdPuzzleId.first()
        assertTrue(opened.puzzleId.isNotEmpty())
        assertFalse("a freshly generated board is not a resumed one", opened.resumed)
        assertTrue(puzzleDao.puzzles.containsKey(opened.puzzleId))
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.failed)
    }

    @Test
    fun `a generator that gives up is reported, not thrown`() = runTest(dispatcher) {
        // The generator is bounded on purpose, so it can fail. An uncaught throw here would take
        // the app down over something the player could simply retry.
        val viewModel = NewGameViewModel(repositoryWith(SudokuGenerator(maxAttempts = 0)))
        viewModel.startGame(Difficulty.MASTER)

        assertTrue(viewModel.uiState.value.failed)
        assertFalse(viewModel.uiState.value.isGenerating)

        viewModel.dismissFailure()
        assertFalse(viewModel.uiState.value.failed)
    }

    @Test
    fun `a shared board is opened from its code`() = runTest(dispatcher) {
        val sourcePuzzles = FakePuzzleDao()
        val sourceSaves = FakeSavedGameDao(sourcePuzzles)
        val source = GameRepository(
            puzzleDao = sourcePuzzles,
            dailyPuzzleDao = FakeDailyPuzzleDao(),
            savedGameDao = sourceSaves,
            historyDao = FakeGameHistoryDao(sourceSaves),
            generator = SudokuGenerator(),
            computationDispatcher = dispatcher,
        )
        val shared = source.startNewGame(Difficulty.NORMAL).state.puzzle

        val viewModel = NewGameViewModel(repositoryWith(SudokuGenerator()))
        viewModel.openSharedBoard(BoardCode.encode(shared.givens, shared.generatorVersion))

        val opened = viewModel.createdPuzzleId.first()
        assertEquals(shared.id, opened.puzzleId)
        assertFalse(opened.resumed)
        assertNull(viewModel.uiState.value.importProblem)
    }

    @Test
    fun `each way a pasted code can fail gets its own message`() = runTest(dispatcher) {
        // Three different problems the player can actually do something about: wrong text, damaged
        // code, unplayable board. Collapsing them into one message would leave them guessing.
        val viewModel = NewGameViewModel(repositoryWith(SudokuGenerator()))

        viewModel.openSharedBoard("not a code at all")
        assertEquals(ImportProblem.NOT_A_CODE, viewModel.uiState.value.importProblem)

        val valid = BoardCode.encode(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS), 1)
        viewModel.openSharedBoard(valid.dropLast(4))
        assertEquals(ImportProblem.DAMAGED, viewModel.uiState.value.importProblem)

        viewModel.openSharedBoard(BoardCode.encode(SudokuBoard.EMPTY, 1))
        assertEquals(ImportProblem.NOT_A_PUZZLE, viewModel.uiState.value.importProblem)

        viewModel.dismissFailure()
        assertNull(viewModel.uiState.value.importProblem)
    }
}
