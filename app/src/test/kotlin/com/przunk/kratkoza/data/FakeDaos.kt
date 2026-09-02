package com.przunk.kratkoza.data

import com.przunk.kratkoza.data.local.DifficultyStatisticsRow
import com.przunk.kratkoza.data.local.DailyDifficultyStatisticsRow
import com.przunk.kratkoza.data.local.DailyPuzzleDao
import com.przunk.kratkoza.data.local.DailyPuzzleEntity
import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.DailyStatisticsSummaryRow
import com.przunk.kratkoza.data.local.PuzzleDao
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.local.InProgressCountRow
import com.przunk.kratkoza.data.local.SavedGameSummaryRow
import com.przunk.kratkoza.data.local.SolvedBoardDao
import com.przunk.kratkoza.data.local.SolvedBoardEntity
import com.przunk.kratkoza.data.local.SolvedBoardRow
import com.przunk.kratkoza.domain.game.NO_HISTORY_ROW
import com.przunk.kratkoza.domain.model.GameType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins for the Room DAOs.
 *
 * technical_requirements.md section 15 asks for plain fakes over a mocking library. Faking at the
 * DAO seam rather than at the repository is what makes these tests worth having: the repository's
 * own work — the mapping in and out of storage, the statistics aggregation, when a history row is
 * opened and closed — is exercised for real, and only SQLite is replaced.
 *
 * The aggregate queries are reimplemented here in Kotlin. That is a genuine duplication of the SQL,
 * and it is the price of testing the repository without a device; the queries themselves are covered
 * by the instrumented tests.
 */
open class FakePuzzleDao : PuzzleDao {
    val puzzles = mutableMapOf<String, PuzzleEntity>()
    private val writeLock = Any()

    override suspend fun insert(puzzle: PuzzleEntity) {
        synchronized(writeLock) { puzzles.putIfAbsent(puzzle.puzzleId, puzzle) }
    }

    open override suspend fun byId(puzzleId: String): PuzzleEntity? = puzzles[puzzleId]
}

/** In-memory stand-in for the Daily calendar. */
class FakeDailyPuzzleDao : DailyPuzzleDao {
    private val rows = mutableMapOf<Pair<String, String>, DailyPuzzleEntity>()

    override suspend fun insert(entries: List<DailyPuzzleEntity>) {
        for (entry in entries) rows.putIfAbsent(entry.date to entry.difficulty, entry)
    }

    override suspend fun forDate(date: String, rulesetVersion: Int): List<DailyPuzzleEntity> =
        rows.values.filter { it.date == date && it.rulesetVersion == rulesetVersion }

    override suspend fun dateOf(puzzleId: String, rulesetVersion: Int): String? =
        rows.values.firstOrNull { it.puzzleId == puzzleId && it.rulesetVersion == rulesetVersion }?.date

    override suspend fun dateOfPuzzle(puzzleId: String): String? =
        rows.values.filter { it.puzzleId == puzzleId }.maxByOrNull { it.rulesetVersion }?.date

}

class FakeSavedGameDao(private val puzzleDao: FakePuzzleDao) : SavedGameDao {
    private val saves = MutableStateFlow<Map<String, SavedGameEntity>>(emptyMap())
    private val writeLock = Any()

    /** How many times a save has been written; the automatic save is meant to fire on every move. */
    var writeCount: Int = 0
        private set

    override suspend fun upsert(save: SavedGameEntity) {
        synchronized(writeLock) {
            writeCount++
            saves.value = saves.value + (save.puzzleId to save)
        }
    }

    override suspend fun byId(puzzleId: String): SavedGameEntity? = saves.value[puzzleId]

    override suspend fun delete(puzzleId: String) {
        saves.value = saves.value - puzzleId
    }

    override fun observeInProgressByDifficulty(): Flow<List<InProgressCountRow>> = saves.map { current ->
        current.values
            .mapNotNull { puzzleDao.puzzles[it.puzzleId]?.difficulty }
            .groupingBy { it }
            .eachCount()
            .map { (difficulty, count) -> InProgressCountRow(difficulty, count) }
    }

    /** Stands in for the `UPDATE saved_games SET historyId = 0` of the reset transaction. */
    fun detachFromHistory() {
        saves.value = saves.value.mapValues { (_, save) -> save.copy(historyId = NO_HISTORY_ROW) }
    }

    override fun observeAll(): Flow<List<SavedGameSummaryRow>> = saves.map { current ->
        current.values
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { save ->
                val puzzle = puzzleDao.puzzles[save.puzzleId] ?: return@mapNotNull null
                SavedGameSummaryRow(
                    puzzleId = save.puzzleId,
                    startedAt = save.startedAt,
                    lastPlayedAt = save.lastPlayedAt,
                    elapsedMillis = save.elapsedMillis,
                    mistakeCount = save.mistakeCount,
                    board = save.board,
                    difficulty = puzzle.difficulty,
                    gameType = puzzle.gameType,
                    givens = puzzle.givens,
                    solution = puzzle.solution,
                    generatorVersion = puzzle.generatorVersion,
                )
            }
    }

    override fun observeMostRecentId(): Flow<String?> = saves.map { current ->
        current.values.maxByOrNull { it.lastPlayedAt }?.puzzleId
    }

    fun observePuzzleIds(): Flow<Set<String>> = saves.map { it.keys }
}

/** In-memory archive with the same joined projections as [SolvedBoardDao]. */
class FakeSolvedBoardDao(
    private val puzzleDao: FakePuzzleDao,
    private val savedGameDao: FakeSavedGameDao,
) : SolvedBoardDao {
    private val entries = MutableStateFlow<List<SolvedBoardEntity>>(emptyList())
    private val writeLock = Any()

    val all: List<SolvedBoardEntity> get() = entries.value

    override suspend fun insert(entry: SolvedBoardEntity): Long = synchronized(writeLock) {
        val id = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1L
        entries.value = entries.value + entry.copy(id = id)
        id
    }

    override fun observeAll(): Flow<List<SolvedBoardRow>> =
        combine(entries, savedGameDao.observePuzzleIds()) { archive, saves ->
            archive.sortedWith(
                compareByDescending<SolvedBoardEntity> { it.completedAt }
                    .thenByDescending { it.id },
            )
                .mapNotNull { it.toRow(saves) }
        }

    override fun observeById(id: Long): Flow<SolvedBoardRow?> =
        combine(entries, savedGameDao.observePuzzleIds()) { archive, saves ->
            archive.firstOrNull { it.id == id }?.toRow(saves)
        }

    private fun SolvedBoardEntity.toRow(saves: Set<String>): SolvedBoardRow? {
        val puzzle = puzzleDao.puzzles[puzzleId] ?: return null
        return SolvedBoardRow(
            id = id,
            puzzleId = puzzleId,
            completedAt = completedAt,
            elapsedMillis = elapsedMillis,
            mistakeCount = mistakeCount,
            xpEarned = xpEarned,
            score = score,
            difficulty = puzzle.difficulty,
            givens = puzzle.givens,
            generatorVersion = puzzle.generatorVersion,
            seed = puzzle.seed,
            completionCount = entries.value.count { it.puzzleId == puzzleId },
            inProgress = puzzleId in saves,
        )
    }
}

/**
 * Extends the real DAO rather than reimplementing it.
 *
 * That matters here: `resetStatistics` is concrete on [GameHistoryDao], so this fake inherits the
 * actual transaction body and only the storage underneath is swapped. An earlier fake reimplemented
 * the reset, which is precisely why a reset that did the wrong thing passed its tests.
 */
class FakeGameHistoryDao(private val savedGameDao: FakeSavedGameDao? = null) : GameHistoryDao() {
    private val entries = MutableStateFlow<List<GameHistoryEntity>>(emptyList())
    private val writeLock = Any()

    val all: List<GameHistoryEntity> get() = entries.value

    override suspend fun deleteAllHistory() {
        entries.value = emptyList()
    }

    override suspend fun detachSavesFromHistory() {
        savedGameDao?.detachFromHistory()
    }

    override suspend fun insert(entry: GameHistoryEntity): Long {
        return synchronized(writeLock) {
            val id = entries.value.size + 1L
            entries.value = entries.value + entry.copy(id = id)
            id
        }
    }

    override suspend fun markFinished(
        id: Long,
        finishedAt: Long,
        elapsedMillis: Long,
        mistakeCount: Int,
        completed: Boolean,
        xpEarned: Int,
        rankedEligible: Boolean,
        score: Int?,
    ) {
        entries.value = entries.value.map { entry ->
            if (entry.id != id) {
                entry
            } else {
                entry.copy(
                    finishedAt = finishedAt,
                    elapsedMillis = elapsedMillis,
                    mistakeCount = mistakeCount,
                    completed = completed,
                    xpEarned = xpEarned,
                    rankedEligible = rankedEligible,
                    score = score,
                )
            }
        }
    }

    override suspend fun attemptsOn(puzzleIds: List<String>): List<GameHistoryEntity> =
        entries.value.filter { it.puzzleId in puzzleIds }

    override fun observeStartedCount(): Flow<Int> = entries.map { it.size }

    override fun observeCompletedCount(): Flow<Int> = entries.map { list -> list.count { it.completed } }

    override fun observeTotalMistakes(): Flow<Int> =
        entries.map { list -> list.filter { it.completed }.sumOf { it.mistakeCount } }

    override fun observeTotalXp(): Flow<Long> =
        entries.map { list -> list.sumOf { it.xpEarned.toLong() } }

    override fun observeByDifficulty(): Flow<List<DifficultyStatisticsRow>> = entries.map { list ->
        list.groupBy { it.difficulty }.map { (difficulty, rows) ->
            val finished = rows.filter { it.completed }
            DifficultyStatisticsRow(
                difficulty = difficulty,
                startedCount = rows.size,
                completedCount = finished.size,
                bestTimeMillis = finished.minOfOrNull { it.elapsedMillis },
                averageTimeMillis = finished.map { it.elapsedMillis }.averageOrNull(),
                averageMistakes = finished.map { it.mistakeCount }.averageOrNull(),
            )
        }
    }

    override suspend fun dailyScoreSum(puzzleIds: List<String>): Long? =
        entries.value
            .filter { it.gameType == GameType.DAILY.name && it.completed && it.puzzleId in puzzleIds }
            .mapNotNull { it.score }
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.toLong() }

    override suspend fun allTimeDailyScoreSum(): Long? =
        entries.value
            .filter { it.gameType == GameType.DAILY.name && it.completed }
            .mapNotNull { it.score }
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.toLong() }

    override suspend fun markPubliclyRanked(id: Long) {
        entries.value = entries.value.map { if (it.id == id) it.copy(publiclyRanked = true) else it }
    }

    override fun observeDailySummary(): Flow<DailyStatisticsSummaryRow> = entries.map { list ->
        val completed = list.filter { it.gameType == GameType.DAILY.name && it.completed }
        val scores = completed.mapNotNull { it.score }
        DailyStatisticsSummaryRow(
            completedCount = completed.size,
            totalScore = scores.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() },
            bestScore = scores.maxOrNull(),
            averageScore = scores.averageOrNull(),
            rankedEligibleCount = completed.count { it.rankedEligible },
        )
    }

    override fun observeDailyByDifficulty(): Flow<List<DailyDifficultyStatisticsRow>> =
        entries.map { list ->
            list.filter { it.gameType == GameType.DAILY.name && it.completed }
                .groupingBy { it.difficulty }
                .eachCount()
                .map { (difficulty, completedCount) ->
                    DailyDifficultyStatisticsRow(difficulty, completedCount)
                }
        }
}

private fun List<Number>.averageOrNull(): Double? =
    if (isEmpty()) null else sumOf { it.toDouble() } / size
