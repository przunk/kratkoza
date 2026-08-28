package com.przunk.kratkoza.domain.stats

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.progress.Progress

/**
 * The player's own record (project.md section 33).
 *
 * Derived from the history table on every read rather than kept as running totals. project.md
 * section 23 asks that history stay rich enough to recompute statistics later, and a stored total
 * that has drifted from the rows behind it cannot be repaired.
 *
 * Absent for now: the personal score of project.md section 25. Its formula is open (section 39),
 * and a made-up number shown to the player is worse than no number.
 */
data class Statistics(
    val startedCount: Int,
    val completedCount: Int,
    val totalMistakes: Int,
    /** Experience and level (project.md section 32). */
    val progress: Progress,
    val perDifficulty: List<DifficultyStatistics>,
    /** `null` until at least one Daily has been completed, keeping an empty section off screen. */
    val daily: DailyStatistics?,
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
            progress = Progress.NONE,
            perDifficulty = emptyList(),
            daily = null,
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
    /** Games still on the board at this level, waiting to be picked up again. */
    val inProgressCount: Int,
    val bestTimeMillis: Long?,
    val averageTimeMillis: Long?,
    val averageMistakes: Double?,
)

/** The completed Daily record (project.md section 33). */
data class DailyStatistics(
    val completedCount: Int,
    /** Null when the completed rows predate scoring; a real zero score remains `0`. */
    val totalScore: Long?,
    val bestScore: Int?,
    val averageScore: Double?,
    val rankedEligibleCount: Int,
    val perDifficulty: List<DailyDifficultyStatistics>,
)

/** How many Daily boards were completed at one level. */
data class DailyDifficultyStatistics(
    val difficulty: Difficulty,
    val completedCount: Int,
)
