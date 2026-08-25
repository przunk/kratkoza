package com.przunk.kratkoza.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.SavedGameSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Home screen's only job: offer to resume the last game (project.md section 22). */
class HomeViewModel(gameRepository: GameRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = gameRepository.savedGames
        .map { saves -> HomeUiState(mostRecent = saves.firstOrNull()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Immutable
data class HomeUiState(
    /** The most recently played unfinished game, or `null` when there is nothing to continue. */
    val mostRecent: SavedGameSummary? = null,
)
