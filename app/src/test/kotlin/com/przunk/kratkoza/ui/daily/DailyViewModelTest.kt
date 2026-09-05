package com.przunk.kratkoza.ui.daily

import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.FakeSolvedBoardDao
import com.przunk.kratkoza.data.repository.DailyRepository
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.NoRanking
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.SignInOutcome
import com.przunk.kratkoza.domain.ranking.SubmissionOutcome
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import androidx.lifecycle.ViewModelStore
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModelTest {

    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private val dailyPuzzleDao = FakeDailyPuzzleDao()
    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        solvedBoardDao = FakeSolvedBoardDao(puzzleDao, savedGameDao),
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
    )
    private val dailyRepository = DailyRepository(
        games = repository,
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
    )

    /** Fixed, so the day under test never depends on when the suite happens to run. */
    private val noon = Instant.parse("2026-08-26T12:00:00Z")

    /**
     * ViewModels are put in a store so the test can end them the way the framework does.
     *
     * DailyViewModel watches for the changeover with an endless `while (isActive) delay(...)` on
     * the main dispatcher. `runTest` advances virtual time until nothing is pending, so a live
     * watcher means it advances for ever — the suite hangs rather than fails. Clearing the store
     * before the test returns is what ends it, which is what [dailyTest] does.
     */
    private val store = ViewModelStore()

    private fun dailyTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            store.clear()
        }
    }

    private fun viewModelWith(ranking: Ranking, diagnosticsEnabled: Boolean = true): DailyViewModel {
        val viewModel = DailyViewModel(
            dailyRepository,
            ranking,
            diagnosticsEnabled = diagnosticsEnabled,
        ) { noon }
        store.put("daily", viewModel)
        return viewModel
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `without Play Games there is nothing to sign in to`() = dailyTest {
        val viewModel = viewModelWith(NoRanking)

        val state = viewModel.uiState.first { !it.isLoading }
        assertFalse(state.rankingAvailable)
        assertFalse(state.signedIn)
    }

    @Test
    fun `a configured ranking with nobody signed in offers the sign-in`() = dailyTest {
        // The pair the screen reads: something to sign in to, and nobody signed in yet. That is the
        // only combination in which the prompt should appear at all.
        val viewModel = viewModelWith(FakeRanking(signedIn = false))

        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue(state.rankingAvailable)
        assertFalse(state.signedIn)
    }

    @Test
    fun `signing in flips the state that shows the leaderboard buttons`() = dailyTest {
        val ranking = FakeRanking(signedIn = false)
        val viewModel = viewModelWith(ranking)
        viewModel.uiState.first { !it.isLoading }

        viewModel.signIn()

        assertTrue(viewModel.uiState.value.signedIn)
    }

    @Test
    fun `a refused sign-in says so instead of doing nothing`() = dailyTest {
        // The old boolean discarded every reason the SDK supplied, so a completed chooser and a
        // dead press looked identical. Player-facing builds need an answer without developer data.
        val ranking = FakeRanking(signedIn = false)
        ranking.signInOutcome = SignInOutcome.Failed("sign-in — 4 SIGN_IN_REQUIRED")
        val viewModel = viewModelWith(ranking, diagnosticsEnabled = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.signIn()

        // The channel buffers, so the message is already waiting by the time signIn returns.
        assertEquals(DailyViewModel.SIGN_IN_FAILED_KEY, viewModel.messages.first())
        assertFalse(viewModel.uiState.value.signedIn)
    }

    @Test
    fun `a debug build gets the status code rather than the sentence`() = dailyTest {
        // Which code came back is the whole diagnosis, and it is unavailable anywhere else on the
        // device — the SDK reports a refusal to nobody.
        val ranking = FakeRanking(signedIn = false)
        ranking.signInOutcome = SignInOutcome.Failed("sign-in — 4 SIGN_IN_REQUIRED")
        val viewModel = viewModelWith(ranking, diagnosticsEnabled = true)
        viewModel.uiState.first { !it.isLoading }

        viewModel.signIn()

        assertEquals("sign-in — 4 SIGN_IN_REQUIRED", viewModel.messages.first())
    }

    @Test
    fun `a canceled chooser stays quiet in a release build`() = dailyTest {
        val ranking = FakeRanking(signedIn = false)
        ranking.signInOutcome = SignInOutcome.Dismissed("request canceled; verification false")
        val viewModel = viewModelWith(ranking, diagnosticsEnabled = false)
        viewModel.uiState.first { !it.isLoading }

        viewModel.signIn()

        assertNull(
            "a dismissed chooser must leave the message channel empty",
            withTimeoutOrNull(MESSAGE_WAIT_MILLIS) { viewModel.messages.first() },
        )
        assertFalse(viewModel.uiState.value.signedIn)
    }

    @Test
    fun `a canceled chooser exposes its trace in a debug build`() = dailyTest {
        val ranking = FakeRanking(signedIn = false)
        val detail = "request canceled; verification false"
        ranking.signInOutcome = SignInOutcome.Dismissed(detail)
        val viewModel = viewModelWith(ranking, diagnosticsEnabled = true)
        viewModel.uiState.first { !it.isLoading }

        viewModel.signIn()

        assertEquals(detail, viewModel.messages.first())
        assertFalse(viewModel.uiState.value.signedIn)
    }

    @Test
    fun `opening a leaderboard asks for the level whose card was pressed`() = dailyTest {
        // Three levels share one screen and three near-identical identifiers, so which one travels
        // is worth pinning down.
        val ranking = FakeRanking(signedIn = true)
        val viewModel = viewModelWith(ranking)
        viewModel.uiState.first { !it.isLoading }

        viewModel.showLeaderboard(Difficulty.EXPERT)

        assertEquals(listOf(Leaderboard.DAILY_EXPERT), ranking.shown)
    }

    @Test
    fun `a leaderboard that will not open says why instead of doing nothing`() = dailyTest {
        // A button that swallows the press is indistinguishable from a broken phone. This is the
        // one ranking failure the player asked for, so it is the one they hear about.
        val ranking = FakeRanking(signedIn = true, outcome = LeaderboardOutcome.Failed("no such board"))
        val viewModel = viewModelWith(ranking)
        viewModel.uiState.first { !it.isLoading }

        viewModel.showLeaderboard(Difficulty.EASY)

        // Only the failure reaches the player. A successful open announces itself by the overlay
        // appearing, and the step-by-step trail now lives in the log rather than in Toasts.
        assertEquals("no such board", viewModel.messages.first())
    }

    @Test
    fun `two presses before opening starts navigate to one Daily game`() = dailyTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModelWith(NoRanking)
        runCurrent()
        val opened = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.openGame.toList(opened)
        }

        viewModel.open(Difficulty.EASY)
        viewModel.open(Difficulty.EASY)
        runCurrent()

        assertEquals(1, opened.size)
        assertEquals(1, historyDao.all.count { it.puzzleId == opened.single() })
        assertEquals(1, savedGameDao.writeCount)
    }
}

/** A ranking that answers whatever the test set up, and remembers what it was asked to show. */
private class FakeRanking(
    override val isAvailable: Boolean = true,
    private var signedIn: Boolean = false,
    private val outcome: LeaderboardOutcome = LeaderboardOutcome.Shown("test"),
) : Ranking {

    val shown = mutableListOf<Leaderboard>()

    override suspend fun isSignedIn(): Boolean = signedIn

    var signInOutcome: SignInOutcome = SignInOutcome.Signed("test")

    override suspend fun signIn(): SignInOutcome {
        if (signInOutcome is SignInOutcome.Signed) signedIn = true
        return signInOutcome
    }

    override suspend fun show(board: Leaderboard): LeaderboardOutcome {
        shown += board
        return outcome
    }

    override suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome =
        SubmissionOutcome.Submitted(newBest = true)
}

/** Long enough for a message to arrive if one were coming; virtual time makes it cost nothing. */
private const val MESSAGE_WAIT_MILLIS = 1_000L
