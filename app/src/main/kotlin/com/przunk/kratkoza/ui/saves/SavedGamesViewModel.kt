package com.przunk.kratkoza.ui.saves

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.SavedGameSummary
import com.przunk.kratkoza.domain.share.BoardCode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Unfinished games, newest first (project.md section 22). */
class SavedGamesViewModel(private val gameRepository: GameRepository) : ViewModel() {

    val uiState: StateFlow<SavedGamesUiState> = gameRepository.savedGames
        .map { SavedGamesUiState(saves = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SavedGamesUiState(),
        )

    /** The code to hand somebody so they can play this exact board. */
    fun shareCodeFor(save: SavedGameSummary): String =
        BoardCode.encode(save.givens, save.generatorVersion)

    /**
     * Drops a save. The game's history row stays, so an abandoned game still counts as started but
     * not completed (project.md section 33).
     */
    fun delete(puzzleId: String) {
        viewModelScope.launch { gameRepository.deleteSave(puzzleId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Immutable
data class SavedGamesUiState(
    val saves: List<SavedGameSummary> = emptyList(),
    val isLoading: Boolean = true,
)
