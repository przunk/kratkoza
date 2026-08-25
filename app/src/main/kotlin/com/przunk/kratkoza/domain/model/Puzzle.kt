package com.przunk.kratkoza.domain.model

import com.przunk.kratkoza.domain.sudoku.SudokuBoard

/**
 * One sudoku board with everything needed to identify it for good (project.md section 6).
 *
 * The identity is what ties a save, a history row, a statistic and — later — a leaderboard entry to
 * the same board, so it has to survive app restarts and app versions. [id] is derived from the
 * givens rather than randomly assigned, which means the same board always carries the same id no
 * matter which device or which run produced it.
 */
data class Puzzle(
    val id: String,
    /** Which generator produced this board; grading rules may change between versions. */
    val generatorVersion: Int,
    val difficulty: Difficulty,
    val gameType: GameType,
    /** The starting board. Its filled cells are the givens and are never editable. */
    val givens: SudokuBoard,
    /** The one correct completion. project.md section 16 defines a mistake against this. */
    val solution: SudokuBoard,
    /** Set when the board came from a deterministic generator run; `null` otherwise. */
    val seed: Long?,
) {
    /** True when [index] holds a given and the player may not change it. */
    fun isGiven(index: Int): Boolean = !givens.isEmpty(index)

    val givenCount: Int get() = givens.filledCount
}
