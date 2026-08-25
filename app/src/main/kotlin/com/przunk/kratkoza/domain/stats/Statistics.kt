package com.przunk.kratkoza.domain.stats

import com.przunk.kratkoza.domain.model.Difficulty

/**
 * The player's own record (project.md section 33).
 *
 * Derived from the history table on every read rather than kept as running totals. project.md
 * section 23 asks that history stay rich enough to recompute statistics later, and a stored total
 * that has drifted from the rows behind it cannot be repaired.
 *
 * Absent for now: personal score, XP and rank. Their formulas are open (project.md section 39), and
 * a made-up number shown to the player is worse than no number.
 */
data class Statistics(
    val startedCount: Int,
    val completedCount: Int,
    val totalMistakes: Int,
    val perDifficulty: List<DifficultyStatistics>,
) {
    /** `0f` when nothing has been started, rather than a division by zero. */
    val completionRate: Float
        get() = if (startedCount == 0) 0f else completedCount.toFloat() / startedCount

    /** Mistakes per completed game, or `null` when nothing has been completed. */
    val averageMistakes: Double?
        get() = if (completedCount == 0) null else totalMistakes.toDouble() / completedCount

    companion object {
        val EMPTY = Statistics(
            startedCount = 0,
            completedCount = 0,
            totalMistakes = 0,
            perDifficulty = emptyList(),
        )
    }
}

/**
 * One level's record. The aggregates are `null` rather than zero when nothing has been completed at
 * this level: "no best time yet" and "best time of zero" are different statements.
 */
data class DifficultyStatistics(
    val difficulty: Difficulty,
    val startedCount: Int,
    val completedCount: Int,
    val bestTimeMillis: Long?,
    val averageTimeMillis: Long?,
    val averageMistakes: Double?,
)
