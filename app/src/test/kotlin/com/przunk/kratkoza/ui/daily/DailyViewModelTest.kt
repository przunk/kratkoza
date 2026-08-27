package com.przunk.kratkoza.ui.daily

import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.NoRanking
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.SubmissionOutcome
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import androidx.lifecycle.ViewModelStore
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModelTest {

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

    private fun viewModelWith(ranking: Ranking): DailyViewModel {
        val viewModel = DailyViewModel(repository, ranking) { noon }
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
    fun `opening a leaderboard asks for the level whose card was pressed`() = dailyTest {
        // Three levels share one screen and three near-identical identifiers, so which one travels
        // is worth pinning down.
        val ranking = FakeRanking(signedIn = true)
        val viewModel = viewModelWith(ranking)
        viewModel.uiState.first { !it.isLoading }

        viewModel.showLeaderboard(Difficulty.MASTER)

        assertEquals(listOf(Leaderboard.DAILY_MASTER), ranking.shown)
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

    override suspend fun signIn(): Boolean {
        signedIn = true
        return true
    }

    override suspend fun show(board: Leaderboard): LeaderboardOutcome {
        shown += board
        return outcome
    }

    override suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome =
        SubmissionOutcome.Submitted(newBest = true)
}
