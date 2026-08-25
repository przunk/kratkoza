package com.przunk.kratkoza.ui.game

import androidx.compose.runtime.Immutable
import com.przunk.kratkoza.domain.game.CandidateGrid
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.SudokuBoard

/**
 * Everything the game screen draws, and nothing it has to work out for itself
 * (technical_requirements.md section 4: immutable UI state, one direction of flow).
 *
 * The highlight sets are computed in the ViewModel rather than in the board composable. They are
 * derived from the selection by pure geometry, but recomputing them per cell per frame would be 81
 * set builds a frame, and keeping them here means the board can be drawn from data alone.
 */
@Immutable
data class GameUiState(
    val phase: Phase = Phase.LOADING,
    val board: SudokuBoard = SudokuBoard.EMPTY,
    val givens: SudokuBoard = SudokuBoard.EMPTY,
    val candidates: CandidateGrid = CandidateGrid.EMPTY,
    /** Cells holding a digit that disagrees with the solution (project.md section 16). */
    val incorrectCells: Set<Int> = emptySet(),
    val selectedCell: Int? = null,
    /** The selected cell's row, column and block (project.md section 8). */
    val peerCells: Set<Int> = emptySet(),
    /** Cells holding the same digit as the selected one (project.md section 10). */
    val sameDigitCells: Set<Int> = emptySet(),
    val difficulty: Difficulty = Difficulty.EASY,
    val mistakeCount: Int = 0,
    val elapsedMillis: Long = 0,
    val isPaused: Boolean = false,
    /** Whether the number pad writes pencil marks instead of digits (project.md section 14). */
    val candidateMode: Boolean = false,
    val canUndo: Boolean = false,
    /** How many of each digit 1..9 are still missing; index 0 is unused. */
    val remainingByDigit: List<Int> = List(10) { 0 },
    /** project.md section 19, an option. */
    val showRemainingCounts: Boolean = true,
) {
    enum class Phase {
        /** The board is still being read. */
        LOADING,
        PLAYING,

        /** Correctly solved (project.md section 16); the clock has stopped. */
        SOLVED,

        /**
         * The save is gone, or too damaged to restore. The screen leaves rather than showing a
         * board that is not really there.
         */
        MISSING,
    }

    val isPlayable: Boolean get() = phase == Phase.PLAYING && !isPaused
}
