package com.przunk.kratkoza.ui.game

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.FakeSolvedBoardDao
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The number pad and the eraser act on the selected cell.
 *
 * Without a selection they used to look alive and swallow the press, which is the failure
 * ARCHITECTURE.md section 9 exists to stop. These tests are what keeps them dimmed instead.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()

    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
        savedGameDao = savedGameDao,
        historyDao = FakeGameHistoryDao(savedGameDao),
        solvedBoardDao = FakeSolvedBoardDao(puzzleDao, savedGameDao),
        generator = SudokuGenerator(),
        computationDispatcher = dispatcher,
    )

    private lateinit var game: SavedGame
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        game = runBlocking { repository.startNewGame(Difficulty.EASY) }
        viewModel = GameViewModel(
            puzzleId = game.state.puzzle.id,
            gameRepository = repository,
            settingsFlow = MutableStateFlow(GameSettings.DEFAULT),
            debugTools = false,
        )

        compose.setContent {
            KratkozaTheme {
                GameScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNewGame = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the pad and the eraser are dimmed until a cell is chosen`() {
        digitKey(1).assertIsNotEnabled()
        eraser().assertIsNotEnabled()
        reveal().assertIsNotEnabled()
    }

    @Test
    fun `choosing a cell wakes them up`() {
        selectFirstEmptyCell()

        digitKey(1).assertIsEnabled()
        eraser().assertIsEnabled()
        reveal().assertIsEnabled()
    }

    @Test
    fun `undo stays out of it — it does not act on a selection`() {
        // The fix had to be narrower than "disable the row": undo and the candidate toggle work
        // perfectly well with nothing selected, and dimming them would have traded one wrong
        // silence for one wrong refusal.
        selectFirstEmptyCell()
        val cell = firstEmptyCell()
        viewModel.enterDigit(game.state.puzzle.solution[cell])
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.game_undo)).assertIsEnabled()
    }

    @Test
    fun `candidate tap still toggles mode`() {
        candidates(enabled = false).performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(viewModel.uiState.value.candidateMode)
    }

    @Test
    fun `candidate long press asks before clearing and undo restores the marks`() {
        val cell = firstEmptyCell()
        viewModel.selectCell(cell)
        viewModel.toggleCandidateMode()
        viewModel.enterDigit(4)
        compose.waitForIdle()
        val beforeClear = viewModel.uiState.value.candidates

        candidates(enabled = true).performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(viewModel.uiState.value.confirmingCandidateClear)
        compose.onNodeWithText(context.getString(R.string.game_clear_candidates_title))
            .assertIsDisplayed()
        assertEquals(beforeClear, viewModel.uiState.value.candidates)

        compose.onNodeWithText(context.getString(R.string.game_clear_candidates_cancel)).performClick()
        assertEquals(beforeClear, viewModel.uiState.value.candidates)

        candidates(enabled = true).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText(context.getString(R.string.game_clear_candidates_confirm)).performClick()
        compose.waitForIdle()
        assertFalse(viewModel.uiState.value.candidates.hasAny)

        viewModel.undo()
        assertEquals(beforeClear, viewModel.uiState.value.candidates)
    }

    // Driven through the ViewModel rather than by tapping the board: what is under test is that the
    // controls follow the state, not that a cell of that size is hittable at this screen density.
    private fun selectFirstEmptyCell() {
        viewModel.selectCell(firstEmptyCell())
        compose.waitForIdle()
    }

    private fun firstEmptyCell(): Int =
        (0 until Sudoku.CELL_COUNT).first { game.state.puzzle.givens.isEmpty(it) }

    private fun digitKey(digit: Int) =
        compose.onNodeWithContentDescription(context.getString(R.string.game_digit, digit))

    private fun eraser() = compose.onNodeWithText(context.getString(R.string.game_erase))

    private fun reveal() = compose.onNodeWithText(context.getString(R.string.game_reveal_digit))

    private fun candidates(enabled: Boolean) = compose.onNodeWithContentDescription(
        context.getString(if (enabled) R.string.game_candidates_on else R.string.game_candidates_off),
    )
}
