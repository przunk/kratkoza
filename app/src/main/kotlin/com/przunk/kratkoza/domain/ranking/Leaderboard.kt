package com.przunk.kratkoza.domain.ranking

import com.przunk.kratkoza.domain.model.Difficulty

/**
 * The public boards a result can travel to (project.md section 27).
 *
 * Named rather than derived from [Difficulty], because two of them are not about a difficulty at
 * all. Both totals work because Play Games keeps a player's *best* submission and a total only ever
 * grows: the highest number it has seen is therefore the current total, not some peak that has
 * since been passed.
 */
enum class Leaderboard {
    DAILY_EASY,
    DAILY_HARD,
    DAILY_MASTER,

    /**
     * Today's three boards added together.
     *
     * Read through Play Games' own daily span, which resets on its schedule — so the board shows
     * each player's finished day and starts empty on the next one, without the app resetting
     * anything itself.
     */
    DAILY_TOTAL,

    /** Every Daily ever finished, added together. */
    ALL_TIME_TOTAL,

    ;

    companion object {
        /** The board for one Daily level, or `null` for a level Daily does not run. */
        fun forDaily(difficulty: Difficulty): Leaderboard? = when (difficulty) {
            Difficulty.EASY -> DAILY_EASY
            Difficulty.HARD -> DAILY_HARD
            Difficulty.MASTER -> DAILY_MASTER
            Difficulty.NORMAL, Difficulty.EXPERT -> null
        }
    }
}
