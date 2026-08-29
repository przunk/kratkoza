package com.przunk.kratkoza.domain.model

import com.przunk.kratkoza.domain.sudoku.SolvingTechnique

/**
 * A sudoku with a unique solution needs at least 17 givens, so no board can go below this. Declared
 * at file level rather than in the companion object because enum entries are constructed before
 * their own companion exists.
 */
private const val ABSOLUTE_MINIMUM = 17

/** What separates [Difficulty.EASY] from [Difficulty.NORMAL]. */
private const val EASY_MINIMUM = 30

/**
 * The five difficulty levels of project.md section 7, all available from the start.
 *
 * A level is primarily defined by [hardestRequiredTechnique] — the hardest technique a board of this
 * level may *require*. project.md section 5 rules out deciding difficulty by clue count alone, and
 * this is how that is honoured.
 *
 * [EASY] and [NORMAL] share a technique ceiling and are told apart by [minimumGivens]. Measurement
 * on this generator (see docs/OPEN_QUESTIONS.md) shows that boards dug as far as uniqueness allows
 * are still solvable with singles alone roughly two thirds of the time, so a single-technique ladder
 * leaves the bottom of the scale with one level instead of two. Giving [EASY] a clue floor splits it
 * honestly: both levels need only singles, and the easy one simply starts with more of the work
 * done. Clue count is a tie-breaker inside one technique tier here, never the axis itself.
 *
 * project.md section 39 leaves both the final names and the exact grading open, so these thresholds
 * are the app's current working answer. They live in one place so that revisiting them is one edit.
 */
enum class Difficulty(
    /** Boards of this level are solvable without ever needing a technique harder than this. */
    val hardestRequiredTechnique: SolvingTechnique,
    /** Fewest givens a board of this level may show. */
    val minimumGivens: Int,
) {
    /** Singles only, and generously clued. */
    EASY(SolvingTechnique.HIDDEN_SINGLE, minimumGivens = EASY_MINIMUM),

    /** Singles only, but dug as far as a unique solution allows. */
    NORMAL(SolvingTechnique.HIDDEN_SINGLE, minimumGivens = ABSOLUTE_MINIMUM),

    /** Needs locked candidates — pointing or claiming. */
    HARD(SolvingTechnique.LOCKED_CANDIDATES, minimumGivens = ABSOLUTE_MINIMUM),

    /** Needs a naked or hidden pair. */
    EXPERT(SolvingTechnique.HIDDEN_PAIR, minimumGivens = ABSOLUTE_MINIMUM),

    /** Needs a triple, an X-Wing, an XY-Wing or a Swordfish. */
    MASTER(SolvingTechnique.SWORDFISH, minimumGivens = ABSOLUTE_MINIMUM),
    ;

    /** Every technique a solver may use while staying inside this level. */
    val allowedTechniques: Set<SolvingTechnique>
        get() = SolvingTechnique.upTo(hardestRequiredTechnique)

    /** The next level down, or `null` for [EASY]. */
    val easier: Difficulty? get() = entries.getOrNull(ordinal - 1)

    companion object {
        /**
         * A sudoku with a unique solution needs at least 17 givens; no board can go below this, so
         * it is the floor for every level that has no floor of its own.
         */
        const val ABSOLUTE_MINIMUM_GIVENS = ABSOLUTE_MINIMUM

        /**
         * What separates [EASY] from [NORMAL]. Chosen to sit in the range a printed "easy" sudoku
         * normally uses, and comfortably above the ~24 givens a maximal dig reaches.
         */
        const val EASY_MINIMUM_GIVENS = EASY_MINIMUM

        /** Stable identifier for storage; [name] is deliberately not user-facing text. */
        fun fromStorageName(value: String): Difficulty? = entries.firstOrNull { it.name == value }
    }
}
