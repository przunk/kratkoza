package com.przunk.kratkoza.domain.share

import com.przunk.kratkoza.domain.model.Difficulty

/** What came of trying to open a board somebody shared. */
sealed interface BoardImportResult {

    /**
     * The board is ready to play. [difficulty] is graded here rather than taken from the code, so
     * it describes the board actually being opened.
     *
     * [alreadyStarted] means this exact board is already in the saved games — the same puzzle
     * shared twice, or shared back. The game is resumed rather than restarted, because throwing
     * away somebody's progress to honour a paste is not a trade worth making.
     */
    data class Ready(
        val puzzleId: String,
        val difficulty: Difficulty,
        val alreadyStarted: Boolean,
    ) : BoardImportResult

    /** The text was not a board code. */
    data object NotABoardCode : BoardImportResult

    /** A board code, but corrupted in transit. */
    data object Damaged : BoardImportResult

    /**
     * The code was read, but the board is not a sudoku worth playing: no solution, more than one,
     * or none reachable without guessing.
     *
     * project.md section 5 requires exactly one solution reachable by logic, and a board arriving
     * from outside has not been through the generator that guarantees it.
     */
    data object NotAValidPuzzle : BoardImportResult
}
