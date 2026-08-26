package com.przunk.kratkoza.ui.daily

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.daily.DailyEntry
import com.przunk.kratkoza.domain.daily.DailySchedule
import com.przunk.kratkoza.domain.model.Difficulty
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
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyUiState())
    val uiState: StateFlow<DailyUiState> = _uiState.asStateFlow()

    private val _openGame = Channel<String>(Channel.BUFFERED)
    val openGame = _openGame.receiveAsFlow()

    init {
        viewModelScope.launch { load(DailySchedule.dailyDateAt(clock())) }
        viewModelScope.launch { watchForChangeover() }
    }

    /** Reloads the day's state — the statuses change as soon as a game is played and returned from. */
    fun refresh() {
        viewModelScope.launch { load(DailySchedule.dailyDateAt(clock())) }
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
        viewModelScope.launch {
            val game = gameRepository.startDaily(date, difficulty)
            if (game == null) load(date) else _openGame.send(game.state.puzzle.id)
        }
    }

    private suspend fun load(date: LocalDate) {
        // Generating three boards takes a moment on a first visit, so the screen says so rather
        // than looking broken.
        _uiState.value = _uiState.value.copy(isLoading = true)
        val entries = gameRepository.dailySet(date)
        _uiState.value = DailyUiState(
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

    private companion object {
        /** Once a second: the screen shows a countdown, and a coarser tick makes it stutter. */
        const val TICK_MILLIS = 1_000L
    }
}

@Immutable
data class DailyUiState(
    val date: LocalDate? = null,
    val entries: List<DailyEntry> = emptyList(),
    /** Until the next set replaces this one. */
    val remainingMillis: Long = 0,
    val isLoading: Boolean = true,
)
