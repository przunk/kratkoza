package com.przunk.kratkoza.ui.history

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.FakeSolvedBoardDao
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import com.przunk.kratkoza.util.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class SolvedBoardHistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val puzzles = FakePuzzleDao()
    private val saves = FakeSavedGameDao(puzzles)
    private val history = FakeGameHistoryDao(saves)
    private val archive = FakeSolvedBoardDao(puzzles, saves)
    private var now = 10_000L
    private val repository = GameRepository(
        puzzleDao = puzzles,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
        savedGameDao = saves,
        historyDao = history,
        solvedBoardDao = archive,
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
        clock = { now },
    )

    @Test
    fun `history row shows the initial board result count and quick share action`() {
        val archiveId = seedCompletion()
        val viewModel = SolvedBoardHistoryViewModel(repository)
        var opened: Long? = null

        compose.setContent {
            KratkozaTheme {
                SolvedBoardHistoryScreen(
                    viewModel = viewModel,
                    onOpenDetail = { opened = it },
                    onNavigateBack = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.difficulty_hard)).assertIsDisplayed()
        compose.onNodeWithText(formatDateTime(context, now)).assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.history_solved_count, 1, 1),
        ).assertIsDisplayed()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.hint_count, 2, 2),
            substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.history_preview))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.history_share))
            .assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.difficulty_hard)).performClick()
        assertEquals(archiveId, opened)
    }

    @Test
    fun `detail offers solve again and opens the replayed game`() {
        val archiveId = seedCompletion()
        val viewModel = SolvedBoardDetailViewModel(archiveId, repository)
        var openedPuzzleId: String? = null

        compose.setContent {
            KratkozaTheme {
                SolvedBoardDetailScreen(
                    viewModel = viewModel,
                    onOpenGame = { openedPuzzleId = it },
                    onNavigateBack = {},
                )
            }
        }

        val solveAgain = context.getString(R.string.history_solve_again)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(solveAgain))
        compose.onNodeWithText(solveAgain).assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(runBlocking { repository.solvedBoards.first().single().puzzleId }, openedPuzzleId)
        compose.onNodeWithText(context.getString(R.string.history_continue)).assertIsDisplayed()
    }

    private fun seedCompletion(): Long = runBlocking {
        val game = repository.startNewGame(Difficulty.HARD)
        repository.finishGame(game.copy(elapsedMillis = 75_000, hintCount = 2), completed = true)
        repository.solvedBoards.first().single().id
    }
}
