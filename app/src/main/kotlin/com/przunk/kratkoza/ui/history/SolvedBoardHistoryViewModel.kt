package com.przunk.kratkoza.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.SolvedBoard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The permanent archive of successful Solo completions, newest first. */
class SolvedBoardHistoryViewModel(private val gameRepository: GameRepository) : ViewModel() {

    val uiState: StateFlow<SolvedBoardHistoryUiState> = gameRepository.solvedBoards
        .map { SolvedBoardHistoryUiState(boards = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SolvedBoardHistoryUiState(),
        )

    fun shareCodeFor(board: SolvedBoard): String = gameRepository.shareCodeFor(board)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** One archived completion and the command that can reopen its board. */
class SolvedBoardDetailViewModel(
    archiveId: Long,
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val opening = MutableStateFlow(false)

    val uiState: StateFlow<SolvedBoardDetailUiState> = combine(
        gameRepository.solvedBoard(archiveId),
        opening,
    ) { board, isOpening ->
        SolvedBoardDetailUiState(board = board, isLoading = false, isOpening = isOpening)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SolvedBoardDetailUiState(),
        )

    private val _openedPuzzleId = Channel<String>(Channel.BUFFERED)
    val openedPuzzleId = _openedPuzzleId.receiveAsFlow()

    fun shareCodeFor(board: SolvedBoard): String = gameRepository.shareCodeFor(board)

    fun play() {
        val state = uiState.value
        val board = state.board ?: return
        if (opening.value) return
        opening.value = true

        viewModelScope.launch {
            val game = try {
                gameRepository.replaySolvedBoard(board.puzzleId)
            } finally {
                opening.value = false
            }
            if (game != null) _openedPuzzleId.send(game.state.puzzle.id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Immutable
data class SolvedBoardHistoryUiState(
    val boards: List<SolvedBoard> = emptyList(),
    val isLoading: Boolean = true,
)

@Immutable
data class SolvedBoardDetailUiState(
    val board: SolvedBoard? = null,
    val isLoading: Boolean = true,
    val isOpening: Boolean = false,
)
