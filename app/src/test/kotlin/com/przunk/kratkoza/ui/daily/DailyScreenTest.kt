package com.przunk.kratkoza.ui.daily

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.FakeSolvedBoardDao
import com.przunk.kratkoza.data.repository.DailyRepository
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.SignInOutcome
import com.przunk.kratkoza.domain.ranking.SubmissionOutcome
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import java.time.Instant
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
@Config(qualifiers = "en-rUS")
@OptIn(ExperimentalCoroutinesApi::class)
class DailyScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `pressing sign in shows the diagnostic trace in a Snackbar`() {
        val puzzleDao = FakePuzzleDao()
        val savedGameDao = FakeSavedGameDao(puzzleDao)
        val historyDao = FakeGameHistoryDao(savedGameDao)
        val dailyPuzzleDao = FakeDailyPuzzleDao()
        val generator = SudokuGenerator()
        val games = GameRepository(
            puzzleDao = puzzleDao,
            dailyPuzzleDao = dailyPuzzleDao,
            savedGameDao = savedGameDao,
            historyDao = historyDao,
            solvedBoardDao = FakeSolvedBoardDao(puzzleDao, savedGameDao),
            generator = generator,
            computationDispatcher = Dispatchers.Unconfined,
        )
        val dailyRepository = DailyRepository(
            games = games,
            puzzleDao = puzzleDao,
            dailyPuzzleDao = dailyPuzzleDao,
            savedGameDao = savedGameDao,
            historyDao = historyDao,
            generator = generator,
            computationDispatcher = Dispatchers.Unconfined,
        )
        val viewModel = DailyViewModel(
            dailyRepository = dailyRepository,
            ranking = FailingSignInRanking,
            diagnosticsEnabled = true,
            clock = { Instant.parse("2026-08-26T12:00:00Z") },
        )
        store.put("daily", viewModel)

        compose.setContent {
            KratkozaTheme {
                DailyScreen(
                    viewModel = viewModel,
                    onOpenGame = {},
                    onNavigateBack = {},
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { !viewModel.uiState.value.isLoading }

        val signIn = context.getString(R.string.daily_sign_in)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(signIn))
        compose.onNodeWithText(signIn).performClick()

        compose.onNodeWithText(DIAGNOSTIC_TRACE).assertIsDisplayed()
    }

    private object FailingSignInRanking : Ranking {
        override val isAvailable: Boolean = true
        override suspend fun isSignedIn(): Boolean = false
        override suspend fun signIn(): SignInOutcome = SignInOutcome.Failed(DIAGNOSTIC_TRACE)
        override suspend fun show(board: Leaderboard): LeaderboardOutcome =
            LeaderboardOutcome.Failed("not used")
        override suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome =
            SubmissionOutcome.Failed("not used")
    }

    private companion object {
        const val DIAGNOSTIC_TRACE =
            "request{success=false, status=10}; verification{success=true, authenticated=false}"
    }
}
