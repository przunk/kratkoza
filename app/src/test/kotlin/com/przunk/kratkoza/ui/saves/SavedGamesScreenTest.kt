package com.przunk.kratkoza.ui.saves

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.FakeSolvedBoardDao
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.GameFixtures
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import com.przunk.kratkoza.util.formatDateTime
import com.przunk.kratkoza.util.formatDuration
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
@OptIn(ExperimentalCoroutinesApi::class)
class SavedGamesScreenTest {

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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `empty state shows its message without a phantom save card`() {
        showScreen()

        compose.onNodeWithText(context.getString(R.string.saved_games_empty)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.saved_games_preview))
            .assertCountEquals(0)
    }

    @Test
    fun `save row shows difficulty elapsed time progress mistakes and date`() {
        val completedCells = 3
        val elapsedMillis = 125_000L
        val lastPlayedAt = 1_700_000_000_000L
        val mistakes = 2
        seedSave(
            puzzleId = "details-save",
            difficulty = Difficulty.HARD,
            elapsedMillis = elapsedMillis,
            lastPlayedAt = lastPlayedAt,
            mistakes = mistakes,
            completedCells = completedCells,
        )

        showScreen()

        compose.onNodeWithText(
            context.getString(
                R.string.saved_games_entry,
                context.getString(Difficulty.HARD.labelRes()),
                formatDuration(context, elapsedMillis),
            ),
        ).assertIsDisplayed()
        compose.onNodeWithText(formatDateTime(context, lastPlayedAt)).assertIsDisplayed()

        val cellsToFill = (0 until Sudoku.CELL_COUNT).count(GameFixtures.PUZZLE.givens::isEmpty)
        val progress = NumberFormat.getPercentInstance()
            .format(completedCells.toFloat() / cellsToFill)
        val mistakeText = context.resources.getQuantityString(
            R.plurals.mistake_count,
            mistakes,
            mistakes,
        )
        compose.onNodeWithText(
            context.getString(R.string.saved_games_progress, progress, mistakeText),
        ).assertIsDisplayed()
    }

    @Test
    fun `delete needs a second tap and removes the save only after confirmation`() {
        seedSave(puzzleId = "delete-save")
        showScreen()

        compose.onNodeWithContentDescription(context.getString(R.string.saved_games_delete))
            .performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.saved_games_delete_armed))
            .assertIsDisplayed()
        assertNotNull(runBlocking { savedGameDao.byId("delete-save") })

        compose.onNodeWithContentDescription(context.getString(R.string.saved_games_delete_armed))
            .performClick()
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.saved_games_empty)).assertIsDisplayed()
        assertNull(runBlocking { savedGameDao.byId("delete-save") })
    }

    @Test
    fun `armed delete returns to its safe state after three seconds`() {
        seedSave(puzzleId = "timeout-save")
        showScreen()

        compose.onNodeWithContentDescription(context.getString(R.string.saved_games_delete))
            .performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.saved_games_delete_armed))
            .assertIsDisplayed()

        compose.mainClock.advanceTimeBy(3_001)
        compose.waitForIdle()

        compose.onNodeWithContentDescription(context.getString(R.string.saved_games_delete))
            .assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.saved_games_delete_armed))
            .assertCountEquals(0)
        assertNotNull(runBlocking { savedGameDao.byId("timeout-save") })
    }

    @Test
    fun `arming another row disarms the previous row`() {
        seedSave(puzzleId = "older-save", lastPlayedAt = 1_000)
        seedSave(puzzleId = "newer-save", lastPlayedAt = 2_000)
        showScreen()
        val delete = context.getString(R.string.saved_games_delete)
        val confirm = context.getString(R.string.saved_games_delete_armed)

        compose.onAllNodesWithContentDescription(delete).assertCountEquals(2)[0].performClick()
        compose.onAllNodesWithContentDescription(confirm).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(delete).assertCountEquals(1)[0].performClick()

        compose.onAllNodesWithContentDescription(confirm).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(delete).assertCountEquals(1)
        compose.onAllNodesWithText(context.getString(R.string.saved_games_delete_confirm))
            .assertCountEquals(1)
    }

    private fun showScreen() {
        val viewModel = SavedGamesViewModel(repository)
        compose.setContent {
            KratkozaTheme {
                SavedGamesScreen(
                    viewModel = viewModel,
                    onOpenGame = {},
                    onNavigateBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun seedSave(
        puzzleId: String,
        difficulty: Difficulty = Difficulty.NORMAL,
        elapsedMillis: Long = 60_000,
        lastPlayedAt: Long = 1_000,
        mistakes: Int = 0,
        completedCells: Int = 0,
    ) = runBlocking {
        val puzzle = GameFixtures.PUZZLE
        var board = puzzle.givens
        (0 until Sudoku.CELL_COUNT)
            .filter(puzzle.givens::isEmpty)
            .take(completedCells)
            .forEach { cell -> board = board.withValue(cell, puzzle.solution[cell]) }

        puzzleDao.insert(
            PuzzleEntity(
                puzzleId = puzzleId,
                generatorVersion = puzzle.generatorVersion,
                difficulty = difficulty.name,
                gameType = puzzle.gameType.name,
                givens = puzzle.givens.toStorageString(),
                solution = puzzle.solution.toStorageString(),
                seed = null,
            ),
        )
        savedGameDao.upsert(
            SavedGameEntity(
                puzzleId = puzzleId,
                historyId = 1,
                startedAt = 0,
                lastPlayedAt = lastPlayedAt,
                elapsedMillis = elapsedMillis,
                board = board.toStorageString(),
                candidates = "",
                undoHistory = "",
                mistakeCount = mistakes,
                rankedEligible = false,
            ),
        )
    }
}
