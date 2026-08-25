package com.przunk.kratkoza.domain.sudoku

import com.przunk.kratkoza.domain.model.Difficulty

/**
 * Grades a board by the hardest technique its solution demands, and — inside the singles tier — by
 * how many givens it starts with.
 *
 * project.md section 5: difficulty must not follow the number of empty cells. A 26-clue board
 * solvable by hidden singles alone is not a hard puzzle, and a 30-clue board that needs an X-Wing is
 * not an easy one. See [Difficulty] for why the two easiest levels also look at the clue count.
 */
object DifficultyEvaluator {

    /**
     * The grade of [givens], or `null` when [LogicalSolver] cannot finish it.
     *
     * A board that logic cannot finish fails project.md's requirement that every puzzle be solvable
     * by reasoning, so it must never be served to a player.
     */
    fun evaluate(givens: SudokuBoard): Rating? {
        val result = LogicalSolver.solve(givens)
        if (!result.solved) return null

        // No techniques at all means the board arrived already solved; grade it at the bottom.
        val hardest = result.hardestTechnique ?: SolvingTechnique.NAKED_SINGLE
        return Rating(
            difficulty = gradeOf(hardest, givens.filledCount),
            hardestTechnique = hardest,
            techniqueUsage = result.techniqueUsage,
        )
    }

    /** True when [givens] can be solved without going past [difficulty]'s technique ceiling. */
    fun isSolvableWithin(givens: SudokuBoard, difficulty: Difficulty): Boolean =
        LogicalSolver.solve(givens, difficulty.allowedTechniques).solved

    /**
     * The level a board belongs to, given the hardest technique it needs and how many givens it
     * shows.
     *
     * Read top to bottom: the first level whose ceiling covers [hardestTechnique] wins, except that
     * the singles tier is split by [Difficulty.EASY]'s clue floor.
     */
    private fun gradeOf(hardestTechnique: SolvingTechnique, givenCount: Int): Difficulty {
        val tier = Difficulty.entries
            .drop(1) // EASY and NORMAL share a ceiling; NORMAL stands for the pair here.
            .firstOrNull { hardestTechnique.rank <= it.hardestRequiredTechnique.rank }
            ?: Difficulty.MASTER

        return if (tier == Difficulty.NORMAL && givenCount >= Difficulty.EASY_MINIMUM_GIVENS) {
            Difficulty.EASY
        } else {
            tier
        }
    }

    data class Rating(
        val difficulty: Difficulty,
        val hardestTechnique: SolvingTechnique,
        /** How often each technique fired; kept for tuning the thresholds later. */
        val techniqueUsage: Map<SolvingTechnique, Int>,
    )
}
