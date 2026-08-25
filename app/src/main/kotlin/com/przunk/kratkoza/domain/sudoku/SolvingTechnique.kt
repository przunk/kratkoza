package com.przunk.kratkoza.domain.sudoku

/**
 * The human solving techniques [LogicalSolver] knows, ordered by how hard they are to spot at the
 * table. [rank] is what difficulty grading compares, so the order of the entries is the definition
 * of "harder" in this app.
 *
 * project.md section 5 requires difficulty to follow the techniques a puzzle demands rather than its
 * clue count, and project.md section 39 leaves the exact grading open. This ladder is therefore the
 * app's working answer, not a frozen one: adding a technique means inserting it at its rank and
 * re-checking [com.przunk.kratkoza.domain.model.Difficulty].
 */
enum class SolvingTechnique(val rank: Int) {
    /** A cell has exactly one candidate left. */
    NAKED_SINGLE(1),

    /** Within a unit, a digit fits in exactly one cell. */
    HIDDEN_SINGLE(2),

    /**
     * A digit's candidates inside a block all sit in one row or column (or the reverse), so it can
     * be struck from the rest of that line (or block). Pointing and claiming, graded together.
     */
    LOCKED_CANDIDATES(3),

    /** Two cells in a unit share the same two candidates, locking both digits away from the rest. */
    NAKED_PAIR(4),

    /** Two digits in a unit are confined to the same two cells, clearing those cells of the rest. */
    HIDDEN_PAIR(5),

    /** Three cells in a unit span exactly three candidates between them. */
    NAKED_TRIPLE(6),

    /** Three digits in a unit are confined to the same three cells. */
    HIDDEN_TRIPLE(7),

    /** A digit forms a 2x2 rectangle of candidates across two rows and two columns. */
    X_WING(8),

    /** A bivalue pivot with two bivalue pincers eliminates their shared third digit. */
    XY_WING(9),

    /** The 3x3 generalisation of [X_WING]. */
    SWORDFISH(10),
    ;

    companion object {
        /** Everything the solver can do, i.e. the widest grading scale available. */
        val ALL: Set<SolvingTechnique> = entries.toSet()

        /** Every technique at or below [rank]. */
        fun upTo(technique: SolvingTechnique): Set<SolvingTechnique> =
            entries.filterTo(mutableSetOf()) { it.rank <= technique.rank }
    }
}
