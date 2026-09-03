package com.przunk.kratkoza.data.repository

import com.przunk.kratkoza.data.local.DailyDifficultyStatisticsRow
import com.przunk.kratkoza.data.local.DailyStatisticsSummaryRow
import com.przunk.kratkoza.data.local.DifficultyStatisticsRow
import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.InProgressCountRow
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.progress.PlayerProgress
import com.przunk.kratkoza.domain.stats.DailyDifficultyStatistics
import com.przunk.kratkoza.domain.stats.DailyStatistics
import com.przunk.kratkoza.domain.stats.DifficultyStatistics
import com.przunk.kratkoza.domain.stats.Statistics
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.combine

/**
 * The player's record (project.md section 33), read straight from history.
 *
 * Split out of [GameRepository]. It shares the same tables but nothing else: everything here is a
 * read over rows somebody else wrote, which is why it was the easiest third of that class to lift
 * out and the least likely to be edited in the same breath as the game itself.
 */
class StatisticsRepository(
    private val historyDao: GameHistoryDao,
    private val savedGameDao: SavedGameDao,
) {

    /**
     * Clears the player's record: every counter goes to zero.
     *
     * Games on the board are untouched and stay playable; they simply stop belonging to the record
     * that was just thrown away, and finishing one afterwards opens a fresh row.
     */
    suspend fun resetStatistics() = historyDao.resetStatistics()

    /** The player's record (project.md section 33), recomputed from history on every change. */
    val statistics: Flow<Statistics> = combine(
        historyDao.observeStartedCount(),
        historyDao.observeCompletedCount(),
        historyDao.observeTotalMistakes(),
        historyDao.observeTotalXp(),
        historyDao.observeByDifficulty(),
        savedGameDao.observeInProgressByDifficulty(),
        historyDao.observeDailySummary(),
        historyDao.observeDailyByDifficulty(),
        historyDao.observeTotalHints(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val perDifficulty = values[4] as List<DifficultyStatisticsRow>

        @Suppress("UNCHECKED_CAST")
        val inProgress = (values[5] as List<InProgressCountRow>).associateBy { it.difficulty }

        val dailySummary = values[6] as DailyStatisticsSummaryRow

        @Suppress("UNCHECKED_CAST")
        val dailyByDifficulty = values[7] as List<DailyDifficultyStatisticsRow>

        Statistics(
            startedCount = values[0] as Int,
            completedCount = values[1] as Int,
            totalMistakes = values[2] as Int,
            totalHints = values[8] as Int,
            progress = PlayerProgress.of(values[3] as Long),
            perDifficulty = Difficulty.entries.map { difficulty ->
                val row = perDifficulty.firstOrNull { it.difficulty == difficulty.name }
                DifficultyStatistics(
                    difficulty = difficulty,
                    startedCount = row?.startedCount ?: 0,
                    completedCount = row?.completedCount ?: 0,
                    inProgressCount = inProgress[difficulty.name]?.inProgressCount ?: 0,
                    bestTimeMillis = row?.bestTimeMillis,
                    averageTimeMillis = row?.averageTimeMillis?.roundToLong(),
                    averageMistakes = row?.averageMistakes,
                )
            },
            daily = dailySummary.takeIf { it.completedCount > 0 }?.let { summary ->
                DailyStatistics(
                    completedCount = summary.completedCount,
                    totalScore = summary.totalScore,
                    bestScore = summary.bestScore,
                    averageScore = summary.averageScore,
                    rankedEligibleCount = summary.rankedEligibleCount,
                    perDifficulty = Difficulty.entries.mapNotNull { difficulty ->
                        dailyByDifficulty.firstOrNull { it.difficulty == difficulty.name }?.let {
                            DailyDifficultyStatistics(
                                difficulty = difficulty,
                                completedCount = it.completedCount,
                            )
                        }
                    },
                )
            },
        )
    }
}
