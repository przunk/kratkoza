package com.przunk.kratkoza.domain.game

/**
 * One undoable step (project.md section 12).
 *
 * A move records what the affected cells looked like *before* it, not what it did. Undo then simply
 * restores that snapshot, which is what makes the rule "undo covers automatic candidate changes too"
 * fall out for free: whatever the move touched is what undo puts back.
 *
 * Deliberately absent: the mistake counter. project.md section 12 requires a committed mistake to
 * survive undo, so it is not part of what a move restores.
 */
data class Move(
    val index: Int,
    /** The digit in [index] before the move, `0` for empty. */
    val previousValue: Int,
    /** The digit in [index] after the move, `0` for empty. */
    val newValue: Int,
    /** Pencil marks before the move, for every cell this move changed. */
    val previousCandidates: Map<Int, Set<Int>>,
)
