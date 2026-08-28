package com.przunk.kratkoza.ui.newgame

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.share.BoardCode
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w600dp-h1000dp")
@OptIn(ExperimentalCoroutinesApi::class)
class NewGameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: NewGameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val puzzleDao = FakePuzzleDao()
        val savedGameDao = FakeSavedGameDao(puzzleDao)
        viewModel = NewGameViewModel(
            GameRepository(
                puzzleDao = puzzleDao,
                dailyPuzzleDao = FakeDailyPuzzleDao(),
                savedGameDao = savedGameDao,
                historyDao = FakeGameHistoryDao(savedGameDao),
                generator = SudokuGenerator(),
                computationDispatcher = dispatcher,
            ),
        )
        compose.setContent {
            KratkozaTheme {
                NewGameScreen(
                    viewModel = viewModel,
                    onGameCreated = {},
                    onNavigateBack = {},
                )
            }
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `all five difficulties are enabled buttons`() {
        for (difficulty in Difficulty.entries) {
            compose.onNodeWithText(context.getString(difficulty.labelRes()))
                .assertHasClickAction()
                .assertIsEnabled()
        }
    }

    @Test
    fun `import dialog accepts pasted text and closes after opening`() {
        openImportDialog()

        compose.onNodeWithText(context.getString(R.string.new_game_import_hint))
            .performTextInput("not a code")
        compose.onNodeWithText(context.getString(R.string.new_game_import_confirm)).performClick()
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()

        compose.onAllNodesWithText(context.getString(R.string.new_game_import_hint))
            .assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.new_game_import_not_a_code))
            .assertIsDisplayed()
    }

    @Test
    fun `each import failure is shown with its specific explanation`() {
        val valid = BoardCode.encode(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS), 1)
        val cases = listOf(
            "not a code at all" to R.string.new_game_import_not_a_code,
            valid.dropLast(4) to R.string.new_game_import_damaged,
            BoardCode.encode(SudokuBoard.EMPTY, 1) to R.string.new_game_import_invalid,
        )

        for ((code, expectedMessage) in cases) {
            openImportDialog()
            compose.onNodeWithText(context.getString(R.string.new_game_import_hint))
                .performTextInput(code)
            compose.onNodeWithText(context.getString(R.string.new_game_import_confirm)).performClick()
            dispatcher.scheduler.runCurrent()
            compose.waitForIdle()

            compose.onNodeWithText(context.getString(expectedMessage)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.new_game_failed_dismiss)).performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText(context.getString(expectedMessage)).assertCountEquals(0)
        }
    }

    private fun openImportDialog() {
        compose.onNodeWithText(context.getString(R.string.new_game_import)).performClick()
        compose.onNodeWithText(context.getString(R.string.new_game_import_hint)).assertIsDisplayed()
    }
}
