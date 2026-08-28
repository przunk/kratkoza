package com.przunk.kratkoza.ui.daily

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.BuildConfig
import com.przunk.kratkoza.domain.daily.DailyEntry
import com.przunk.kratkoza.domain.daily.DailySchedule
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.NoRanking
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.RankingLog
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Today's three boards (project.md section 26).
 *
 * Watches for the 08:00 changeover while the screen is open, so somebody sitting on it at eight in
 * the morning sees the new set arrive instead of stale puzzles that quietly stopped being today's.
 */
class DailyViewModel(
    private val gameRepository: GameRepository,
    /**
     * The public ranking. Defaults to nowhere, so a build without Play Games identifiers — and
     * every test — simply shows no ranking controls rather than showing dead ones.
     */
    private val ranking: Ranking = NoRanking,
    private val rankingLog: RankingLog = RankingLog(),
    /** Whether to show developer detail. Release builds get a sentence a player can read. */
    private val debugTools: Boolean = BuildConfig.DEBUG,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyUiState())
    val uiState: StateFlow<DailyUiState> = _uiState.asStateFlow()

    private val _openGame = Channel<String>(Channel.BUFFERED)
    val openGame = _openGame.receiveAsFlow()

    /** UI courtesy only; [GameRepository.startDaily] owns the actual single-attempt invariant. */
    private var openingGame = false

    /** Things that went wrong in a way the player asked for and should therefore hear about. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { load(DailySchedule.dailyDateAt(clock())) }
        viewModelScope.launch { watchForChangeover() }
        viewModelScope.launch { refreshSignIn() }
    }

    /**
     * Asks the player to sign in to Play Games.
     *
     * Only from a press. The silent attempt Play Games makes at startup is the only automatic one,
     * and an account chooser appearing by itself over a puzzle would be an interruption, not a
     * feature.
     */
    fun signIn() {
        viewModelScope.launch {
            ranking.signIn()
            refreshSignIn()
        }
    }

    /** Opens the Play Games board for one Daily level. */
    fun showLeaderboard(difficulty: Difficulty) {
        val board = Leaderboard.forDaily(difficulty) ?: return
        show(board)
    }

    /** Opens one of the two totals boards. */
    fun showBoard(board: Leaderboard) = show(board)

    private fun show(board: Leaderboard) {
        viewModelScope.launch {
            // Recorded before the SDK is touched, so the log distinguishes a press that produced
            // nothing from one that never arrived.
            rankingLog.add("leaderboard: opening $board")

            when (val outcome = ranking.show(board)) {
                // Success needs no announcement: the overlay appearing is the announcement. Only
                // the log keeps it, and only debug builds show the log.
                is LeaderboardOutcome.Shown -> rankingLog.add("leaderboard: opened ${outcome.target}")

                is LeaderboardOutcome.Failed -> {
                    rankingLog.add("leaderboard: ${outcome.detail}")
                    // A player gets a sentence; the identifier and status code are for whoever is
                    // holding the log, and mean nothing to anybody else.
                    _messages.send(if (debugTools) outcome.detail else FAILED_TO_OPEN_KEY)
                }
            }
        }
    }

    private suspend fun refreshSignIn() {
        val available = ranking.isAvailable
        val signedIn = available && ranking.isSignedIn()
        rankingLog.add("sign-in: configured=$available signedIn=$signedIn")
        _uiState.value = _uiState.value.copy(rankingAvailable = available, signedIn = signedIn)
    }

    /** Reloads the day's state — the statuses change as soon as a game is played and returned from. */
    fun refresh() {
        viewModelScope.launch { load(DailySchedule.dailyDateAt(clock())) }
        // Coming back from Play Games is one of the ways sign-in changes without this screen being
        // rebuilt, so the state is re-read rather than assumed to be still true.
        viewModelScope.launch { refreshSignIn() }
    }

    /**
     * Opens one of the day's boards.
     *
     * A board already solved cannot be opened again — the day's result is recorded and replaying it
     * would only be an attempt to improve a time that has already been taken. The list refreshes
     * instead, so the card catches up if it was showing something stale.
     */
    fun open(difficulty: Difficulty) {
        val date = _uiState.value.date ?: return
        if (openingGame) return
        openingGame = true
        viewModelScope.launch {
            try {
                val game = gameRepository.startDaily(date, difficulty)
                if (game == null) load(date) else _openGame.send(game.state.puzzle.id)
            } finally {
                openingGame = false
            }
        }
    }

    private suspend fun load(date: LocalDate) {
        // Generating three boards takes a moment on a first visit, so the screen says so rather
        // than looking broken.
        _uiState.value = _uiState.value.copy(isLoading = true)
        val entries = gameRepository.dailySet(date)
        _uiState.value = _uiState.value.copy(
            date = date,
            entries = entries,
            remainingMillis = DailySchedule.remainingMillis(date, clock()),
            isLoading = false,
        )
    }

    private suspend fun watchForChangeover() {
        while (viewModelScope.isActive) {
            delay(TICK_MILLIS)
            val now = clock()
            val shown = _uiState.value.date ?: continue
            val current = DailySchedule.dailyDateAt(now)

            if (current != shown) {
                load(current)
            } else {
                _uiState.value = _uiState.value.copy(
                    remainingMillis = DailySchedule.remainingMillis(shown, now),
                )
            }
        }
    }

    companion object {
        /**
         * Stands in for a string resource.
         *
         * The ViewModel has no Context by design, so it names the message and the screen resolves
         * it. Passing a resource id instead would put an `R` reference in a class that is meant to
         * be testable without Android.
         */
        const val FAILED_TO_OPEN_KEY = "leaderboard-unavailable"

        /** Once a second: the screen shows a countdown, and a coarser tick makes it stutter. */
        private const val TICK_MILLIS = 1_000L
    }
}

@Immutable
data class DailyUiState(
    val date: LocalDate? = null,
    val entries: List<DailyEntry> = emptyList(),
    /** Until the next set replaces this one. */
    val remainingMillis: Long = 0,
    val isLoading: Boolean = true,
    /** Whether Play Games is configured at all; false hides every ranking control. */
    val rankingAvailable: Boolean = false,
    val signedIn: Boolean = false,
)
