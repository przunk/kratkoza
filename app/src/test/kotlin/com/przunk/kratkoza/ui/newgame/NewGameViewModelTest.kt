package com.przunk.kratkoza.ui.newgame

import com.przunk.kratkoza.data.FakeGameHistoryDao
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
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = generator,
        computationDispatcher = dispatcher,
    )

    @Test
    fun `a generated game is announced for navigation`() = runTest(dispatcher) {
        val viewModel = NewGameViewModel(repositoryWith(SudokuGenerator()))
        viewModel.startGame(Difficulty.EASY)

        val puzzleId = viewModel.createdPuzzleId.first()
        assertTrue(puzzleId.isNotEmpty())
        assertTrue(puzzleDao.puzzles.containsKey(puzzleId))
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
}
