package com.przunk.kratkoza.domain.progress

import com.przunk.kratkoza.domain.model.Difficulty

/**
 * Experience and player level (project.md section 32).
 *
 * **The hard rule this file must never break:** XP is a badge, not a gate. Section 32 forbids it
 * from locking difficulties, candidates, settings or any part of ordinary play. Nothing here
 * returns a permission, and nothing anywhere should ask it for one.
 *
 * project.md section 39 leaves the formula open; what follows is the agreed shape, not a frozen one.
 */
object PlayerProgress {

    /**
     * XP for finishing a board, by level.
     *
     * Only completion is rewarded, and mistakes take nothing away. Section 32 forbids manufactured
     * grind, so XP follows *playing*; the penalty for guessing belongs to the Daily ranking, where
     * section 29 wants it severe (`score × 0.7^mistakes`). Two different questions, two different
     * numbers — see docs/STATUS.md.
     */
    fun xpForCompleting(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 10
        Difficulty.NORMAL -> 20
        Difficulty.HARD -> 40
        Difficulty.EXPERT -> 70
        Difficulty.MASTER -> 120
    }

    /**
     * Total XP needed to *reach* [level]. Level 1 is where everyone starts, so it costs nothing.
     *
     * Each level costs 100 XP more than the one before: 100 to reach level 2, then 200, then 300.
     * Summed, that is `50 × level × (level - 1)` — 100, 300, 600, 1000, and 4500 by level 10.
     *
     * Rising cost rather than a fixed one, because a fixed cost makes level 40 mean nothing more
     * than level 4. Linear growth of the *step* rather than doubling, because doubling turns into a
     * wall that only a grinder climbs — and grind is exactly what section 32 rules out.
     */
    fun xpToReach(level: Int): Long {
        require(level >= 1) { "Levels start at 1, asked for $level" }
        return LEVEL_STEP_XP.toLong() * level * (level - 1) / 2
    }

    /** What [totalXp] amounts to: the level reached, and how far into it the player is. */
    fun of(totalXp: Long): Progress {
        require(totalXp >= 0) { "XP cannot be negative, got $totalXp" }

        // Counted rather than solved for. The closed form needs a square root, and a square root at
        // an exact threshold rounds whichever way the hardware feels like — which would put a
        // player one XP short of, or one XP past, the level they just earned.
        var level = 1
        while (xpToReach(level + 1) <= totalXp) level++

        val reached = xpToReach(level)
        return Progress(
            totalXp = totalXp,
            level = level,
            xpIntoLevel = totalXp - reached,
            xpForNextLevel = xpToReach(level + 1) - reached,
        )
    }

    /** How much each further level adds to the price of the one before it. */
    private const val LEVEL_STEP_XP = 100
}

/**
 * A player's standing. [xpIntoLevel] and [xpForNextLevel] are what a progress bar needs; there is no
 * top level, so [xpForNextLevel] is always positive.
 */
data class Progress(
    val totalXp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
) {
    /** `0f`..`1f` through the current level. */
    val fractionIntoLevel: Float
        get() = if (xpForNextLevel <= 0) 0f else xpIntoLevel.toFloat() / xpForNextLevel

    companion object {
        val NONE = Progress(totalXp = 0, level = 1, xpIntoLevel = 0, xpForNextLevel = 100)
    }
}
