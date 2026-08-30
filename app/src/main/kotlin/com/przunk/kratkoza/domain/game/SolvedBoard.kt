package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.SudokuBoard

/** One successful Solo completion in the player's permanent board archive. */
data class SolvedBoard(
    val id: Long,
    val puzzleId: String,
    val difficulty: Difficulty,
    /** The board as it looked before play, never the filled solution. */
    val givens: SudokuBoard,
    val generatorVersion: Int,
    val completedAt: Long,
    val elapsedMillis: Long,
    val mistakeCount: Int,
    val xpEarned: Int,
    /** Kept for a future personal Solo score. The current result shown to the player is XP. */
    val score: Int?,
    val completionCount: Int,
    val inProgress: Boolean,
    /** Imported boards have no generator seed; generated Solo boards always do. */
    val imported: Boolean,
)
