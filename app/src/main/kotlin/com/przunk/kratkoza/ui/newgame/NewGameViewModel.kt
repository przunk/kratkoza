package com.przunk.kratkoza.ui.newgame

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Generates a board and hands its id back for navigation.
 *
 * Generation takes tens of milliseconds on a background dispatcher — short, but long enough that the
 * screen shows it is working rather than appearing to ignore the tap.
 */
class NewGameViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGameUiState())
    val uiState: StateFlow<NewGameUiState> = _uiState.asStateFlow()

    /**
     * Emitted once per created game.
     *
     * A channel rather than a state field: navigation is an event, and a field holding "go to
     * game X" would fire again on every configuration change.
     */
    private val _createdPuzzleId = Channel<String>(Channel.BUFFERED)
    val createdPuzzleId = _createdPuzzleId.receiveAsFlow()

    fun startGame(difficulty: Difficulty) {
        if (_uiState.value.isGenerating) return
        _uiState.value = NewGameUiState(isGenerating = true)

        viewModelScope.launch {
            try {
                val game = gameRepository.startNewGame(difficulty)
                _createdPuzzleId.send(game.state.puzzle.id)
            } finally {
                _uiState.value = NewGameUiState(isGenerating = false)
            }
        }
    }
}

@Immutable
data class NewGameUiState(val isGenerating: Boolean = false)
