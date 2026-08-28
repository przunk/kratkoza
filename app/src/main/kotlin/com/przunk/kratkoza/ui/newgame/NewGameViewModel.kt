package com.przunk.kratkoza.ui.newgame

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.share.BoardImportResult
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
    private val _createdPuzzleId = Channel<OpenedGame>(Channel.BUFFERED)
    val createdPuzzleId = _createdPuzzleId.receiveAsFlow()

    fun startGame(difficulty: Difficulty) {
        if (_uiState.value.isGenerating) return
        _uiState.value = NewGameUiState(isGenerating = true)

        viewModelScope.launch {
            _uiState.value = try {
                val game = gameRepository.startNewGame(difficulty)
                _createdPuzzleId.send(OpenedGame(game.state.puzzle.id, resumed = false))
                NewGameUiState()
            } catch (_: IllegalStateException) {
                // The generator gives up after a bounded number of attempts rather than searching
                // forever. Measured hit rates make that all but impossible, but "all but" is not
                // "never", and an uncaught throw here would take the app down over a retryable
                // failure.
                NewGameUiState(failed = true)
            }
        }
    }

    /**
     * Opens a board somebody shared (wishlist item 6).
     *
     * A board that turns out to be already in progress is resumed rather than restarted, so the
     * screen navigates to it exactly as it would to a new one — the difference is only worth a note
     * to the player, not a different flow.
     */
    fun openSharedBoard(code: String) {
        if (_uiState.value.isGenerating) return
        _uiState.value = NewGameUiState(isGenerating = true)

        viewModelScope.launch {
            when (val result = gameRepository.importSharedBoard(code)) {
                is BoardImportResult.Ready -> {
                    _uiState.value = NewGameUiState()
                    _createdPuzzleId.send(OpenedGame(result.puzzleId, result.alreadyStarted))
                }

                BoardImportResult.NotABoardCode ->
                    _uiState.value = NewGameUiState(importProblem = ImportProblem.NOT_A_CODE)

                BoardImportResult.Damaged ->
                    _uiState.value = NewGameUiState(importProblem = ImportProblem.DAMAGED)

                BoardImportResult.NotAValidPuzzle ->
                    _uiState.value = NewGameUiState(importProblem = ImportProblem.NOT_A_PUZZLE)
            }
        }
    }

    fun dismissFailure() {
        _uiState.value = NewGameUiState()
    }
}

/** Why a pasted code could not be opened. Kept apart from the message so the ViewModel holds no resources. */
enum class ImportProblem { NOT_A_CODE, DAMAGED, NOT_A_PUZZLE }

/**
 * A game to navigate to.
 *
 * [resumed] rides along rather than sitting in the screen state because it belongs to *this* opening
 * and nothing else — the screen is about to be left, so a flag left behind in the state would have
 * nobody to inform.
 */
data class OpenedGame(val puzzleId: String, val resumed: Boolean)

@Immutable
data class NewGameUiState(
    val isGenerating: Boolean = false,
    val failed: Boolean = false,
    val importProblem: ImportProblem? = null,
)
