package com.przunk.kratkoza.data

import com.przunk.kratkoza.data.local.DifficultyStatisticsRow
import com.przunk.kratkoza.data.local.DailyPuzzleDao
import com.przunk.kratkoza.data.local.DailyPuzzleEntity
import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.PuzzleDao
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.local.InProgressCountRow
import com.przunk.kratkoza.data.local.SavedGameSummaryRow
import com.przunk.kratkoza.domain.game.NO_HISTORY_ROW
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class FakePuzzleDao : PuzzleDao {
    val puzzles = mutableMapOf<String, PuzzleEntity>()

    override suspend fun insert(puzzle: PuzzleEntity) {
        puzzles.putIfAbsent(puzzle.puzzleId, puzzle)
    }

    override suspend fun byId(puzzleId: String): PuzzleEntity? = puzzles[puzzleId]
}

/** In-memory stand-in for the Daily calendar. */
class FakeDailyPuzzleDao : DailyPuzzleDao {
    private val rows = mutableMapOf<Pair<String, String>, DailyPuzzleEntity>()

    override suspend fun insert(entries: List<DailyPuzzleEntity>) {
        for (entry in entries) rows.putIfAbsent(entry.date to entry.difficulty, entry)
    }

    override suspend fun forDate(date: String, rulesetVersion: Int): List<DailyPuzzleEntity> =
        rows.values.filter { it.date == date && it.rulesetVersion == rulesetVersion }
}

class FakeSavedGameDao(private val puzzleDao: FakePuzzleDao) : SavedGameDao {
    private val saves = MutableStateFlow<Map<String, SavedGameEntity>>(emptyMap())

    /** How many times a save has been written; the automatic save is meant to fire on every move. */
    var writeCount: Int = 0
        private set

    override suspend fun upsert(save: SavedGameEntity) {
        writeCount++
        saves.value = saves.value + (save.puzzleId to save)
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

    val all: List<GameHistoryEntity> get() = entries.value

    override suspend fun deleteAllHistory() {
        entries.value = emptyList()
    }

    override suspend fun detachSavesFromHistory() {
        savedGameDao?.detachFromHistory()
    }

    override suspend fun insert(entry: GameHistoryEntity): Long {
        val id = entries.value.size + 1L
        entries.value = entries.value + entry.copy(id = id)
        return id
    }

    override suspend fun markFinished(
        id: Long,
        finishedAt: Long,
        elapsedMillis: Long,
        mistakeCount: Int,
        completed: Boolean,
        xpEarned: Int,
        rankedEligible: Boolean,
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
}

private fun List<Number>.averageOrNull(): Double? =
    if (isEmpty()) null else sumOf { it.toDouble() } / size
