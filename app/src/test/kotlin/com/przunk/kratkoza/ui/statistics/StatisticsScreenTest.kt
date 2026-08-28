package com.przunk.kratkoza.ui.statistics

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.GameFixtures
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.progress.PlayerProgress
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class StatisticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `empty statistics show zero values none placeholders and a disabled reset`() {
        showScreen()

        scrollToText(context.getString(R.string.statistics_overall))
        compose.onNodeWithText(NumberFormat.getPercentInstance().format(0f)).assertIsDisplayed()

        scrollToText(context.getString(R.string.difficulty_easy))
        // The visible Easy card contributes three placeholders; the fourth belongs to the overall
        // average. An exact count catches either a missing aggregate or a numeric row rendered as
        // "none" without coupling the assertion to row positions in the semantics tree.
        compose.onAllNodesWithText(context.getString(R.string.statistics_none)).assertCountEquals(4)
        compose.onAllNodesWithText(context.getString(R.string.statistics_in_progress))
            .assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.statistics_daily))
            .assertCountEquals(0)

        val reset = context.getString(R.string.statistics_reset)
        scrollToText(reset)
        compose.onNodeWithText(reset).assertIsNotEnabled()
    }

    @Test
    fun `level XP in-progress row and destructive reset follow repository state`() {
        seedCompletedGame(totalXp = 150)
        seedInProgressGame()
        val progress = PlayerProgress.of(150)
        showScreen()

        compose.onNodeWithText(context.getString(R.string.statistics_level, progress.level))
            .assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(
                R.string.statistics_xp_into_level,
                progress.xpIntoLevel.toString(),
                progress.xpForNextLevel.toString(),
                progress.level + 1,
            ),
        ).assertIsDisplayed()
        val rangeInfo = ProgressBarRangeInfo(progress.fractionIntoLevel, 0f..1f, 0)
        compose.onNode(hasProgressBarRangeInfo(rangeInfo)).assertRangeInfoEquals(rangeInfo)

        val inProgress = context.getString(R.string.statistics_in_progress)
        scrollToText(inProgress)
        compose.onAllNodesWithText(inProgress).assertCountEquals(1)

        val reset = context.getString(R.string.statistics_reset)
        scrollToText(reset)
        compose.onNodeWithText(reset).performClick()
        compose.onNodeWithText(context.getString(R.string.statistics_reset_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.statistics_reset_message)).assertIsDisplayed()
    }

    @Test
    fun `Daily section shows completion levels scores and ranking eligibility`() {
        seedDailyGame(
            puzzleId = "daily-easy",
            difficulty = Difficulty.EASY,
            score = 1_234,
            rankedEligible = true,
        )
        seedDailyGame(
            puzzleId = "daily-hard",
            difficulty = Difficulty.HARD,
            score = 3_456,
            rankedEligible = false,
        )
        seedDailyGame(
            puzzleId = "daily-easy-second",
            difficulty = Difficulty.EASY,
            score = 310,
            rankedEligible = true,
        )
        showScreen()

        scrollToText(context.getString(R.string.statistics_daily))
        scrollToText(context.getString(R.string.statistics_daily_completed))
        assertStatRow(R.string.statistics_daily_completed, NumberFormat.getIntegerInstance().format(3))
        scrollToText(context.getString(R.string.statistics_daily_by_difficulty))
        compose.onNode(
            hasText(context.getString(Difficulty.EASY.labelRes())) and
                hasText(NumberFormat.getIntegerInstance().format(2)),
        ).assertIsDisplayed()
        compose.onNode(
            hasText(context.getString(Difficulty.HARD.labelRes())) and
                hasText(NumberFormat.getIntegerInstance().format(1)),
        ).assertIsDisplayed()

        scrollToText(context.getString(R.string.statistics_daily_total_score))
        assertStatRow(R.string.statistics_daily_total_score, NumberFormat.getIntegerInstance().format(5_000))
        scrollToText(context.getString(R.string.statistics_daily_best_score))
        assertStatRow(R.string.statistics_daily_best_score, NumberFormat.getIntegerInstance().format(3_456))
        scrollToText(context.getString(R.string.statistics_daily_average_score))
        val decimalFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
        assertStatRow(R.string.statistics_daily_average_score, decimalFormat.format(5_000.0 / 3.0))
        scrollToText(context.getString(R.string.statistics_daily_ranked_eligible))
        assertStatRow(R.string.statistics_daily_ranked_eligible, NumberFormat.getIntegerInstance().format(2))
    }

    @Test
    fun `unscored Daily renders missing score aggregates as none`() {
        seedDailyGame(
            puzzleId = "daily-without-score",
            difficulty = Difficulty.MASTER,
            score = null,
            rankedEligible = true,
        )
        showScreen()

        scrollToText(context.getString(R.string.statistics_daily_scores))
        compose.onAllNodesWithText(context.getString(R.string.statistics_none)).assertCountEquals(3)
    }

    private fun showScreen() {
        val viewModel = StatisticsViewModel(repository)
        compose.setContent {
            KratkozaTheme {
                StatisticsScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }
        compose.waitForIdle()
    }

    private fun scrollToText(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertStatRow(labelRes: Int, value: String) {
        compose.onNode(hasText(context.getString(labelRes)) and hasText(value)).assertIsDisplayed()
    }

    private fun seedCompletedGame(totalXp: Int) = runBlocking {
        historyDao.insert(
            GameHistoryEntity(
                puzzleId = "completed-for-statistics",
                gameType = GameType.SOLO.name,
                difficulty = Difficulty.MASTER.name,
                startedAt = 1_000,
                finishedAt = 61_000,
                elapsedMillis = 60_000,
                mistakeCount = 2,
                completed = true,
                score = null,
                xpEarned = totalXp,
                publiclyRanked = false,
                rankedEligible = false,
                autoRemoveCandidates = true,
                rulesetVersion = SudokuGenerator.GENERATOR_VERSION,
            ),
        )
    }

    private fun seedInProgressGame() = runBlocking {
        val puzzle = GameFixtures.PUZZLE
        puzzleDao.insert(
            PuzzleEntity(
                puzzleId = "in-progress-for-statistics",
                generatorVersion = puzzle.generatorVersion,
                difficulty = Difficulty.EASY.name,
                gameType = puzzle.gameType.name,
                givens = puzzle.givens.toStorageString(),
                solution = puzzle.solution.toStorageString(),
                seed = null,
            ),
        )
        savedGameDao.upsert(
            SavedGameEntity(
                puzzleId = "in-progress-for-statistics",
                historyId = 1,
                startedAt = 2_000,
                lastPlayedAt = 3_000,
                elapsedMillis = 0,
                board = puzzle.givens.toStorageString(),
                candidates = "",
                undoHistory = "",
                mistakeCount = 0,
                rankedEligible = false,
            ),
        )
    }

    private fun seedDailyGame(
        puzzleId: String,
        difficulty: Difficulty,
        score: Int?,
        rankedEligible: Boolean,
    ) = runBlocking {
        historyDao.insert(
            GameHistoryEntity(
                puzzleId = puzzleId,
                gameType = GameType.DAILY.name,
                difficulty = difficulty.name,
                startedAt = 1_000,
                finishedAt = 61_000,
                elapsedMillis = 60_000,
                mistakeCount = 1,
                completed = true,
                score = score,
                xpEarned = 0,
                publiclyRanked = false,
                rankedEligible = rankedEligible,
                autoRemoveCandidates = true,
                rulesetVersion = SudokuGenerator.GENERATOR_VERSION,
            ),
        )
    }
}
