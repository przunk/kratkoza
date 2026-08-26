package com.przunk.kratkoza.ui.game

import androidx.compose.runtime.Immutable
import com.przunk.kratkoza.domain.game.CandidateGrid
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
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
    val gameType: GameType = GameType.SOLO,
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
    /**
     * Whether finishing would still count for a public ranking (project.md section 28).
     *
     * While true the game cannot be paused, which is the whole of what section 28 asks. Losing it
     * is one-way: a game that went to the background stays unranked even though it becomes
     * pausable, and the board is untouched either way.
     */
    val rankedEligible: Boolean = false,
    /**
     * Cell to digit, for the last gap in a row, column or block (project.md section 17). Empty
     * unless the option is on.
     */
    val eightOfNineHints: Map<Int, Int> = emptyMap(),
    /**
     * The move just played, or `null` at the start of a game (project.md section 36).
     *
     * [MoveEcho.token] rises with every move, which is what lets the board tell "another move" from
     * "the same state redrawn" — a recomposition caused by the clock or by a settings change must
     * not replay an animation.
     */
    val lastMove: MoveEcho? = null,
) {
    /** What the board should react to after a move. Purely decorative; nothing waits on it. */
    @Immutable
    data class MoveEcho(
        val token: Int,
        /** The cell that changed, for the digit's entrance. */
        val cell: Int?,
        /** Cells to flash, if any. */
        val flashCells: Set<Int> = emptySet(),
        val flashKind: FlashKind = FlashKind.NONE,
    )

    /**
     * Why the board is flashing (project.md section 36).
     *
     * One channel for both, because they cannot happen together: a wrong digit never finishes a
     * unit correctly. The two differ in colour and in how loudly they are allowed to speak.
     */
    enum class FlashKind {
        NONE,

        /** A row, column or block just came out right. */
        COMPLETED,

        /** A digit was committed that the solution disagrees with (project.md section 16). */
        MISTAKE,
    }

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

    /** project.md section 28: no pause button while the game still counts. */
    val canPause: Boolean get() = !rankedEligible

    /**
     * Whether the board may be handed around or put aside.
     *
     * Neither applies to a Daily: everybody already has the same board, so sharing its code says
     * nothing, and stepping out of a game that is racing the clock is what section 28 penalises
     * rather than something to offer as a button.
     */
    val canShareAndSave: Boolean get() = gameType != GameType.DAILY
}
