package com.przunk.kratkoza.domain.daily

/**
 * The two running totals the public boards carry (project.md section 27).
 *
 * Both are sums rather than bests, which is what makes them work on a service that keeps only a
 * player's highest submission: a total never goes down, so the highest number the board has seen is
 * the current total.
 */
data class DailyTotals(
    /** Today's finished boards added together. */
    val today: Long,
    /** Every Daily ever finished, added together. */
    val allTime: Long,
)
