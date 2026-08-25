package com.przunk.kratkoza.data

import com.przunk.kratkoza.data.local.DifficultyStatisticsRow
import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.PuzzleDao
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.local.SavedGameSummaryRow
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
                )
            }
    }

    override fun observeMostRecentId(): Flow<String?> = saves.map { current ->
        current.values.maxByOrNull { it.lastPlayedAt }?.puzzleId
    }

    /** Stands in for the `SELECT historyId FROM saved_games` subquery of `deleteFinished`. */
    fun openHistoryIds(): Set<Long> = saves.value.values.mapTo(mutableSetOf()) { it.historyId }
}

class FakeGameHistoryDao(private val savedGameDao: FakeSavedGameDao? = null) : GameHistoryDao {
    private val entries = MutableStateFlow<List<GameHistoryEntity>>(emptyList())

    val all: List<GameHistoryEntity> get() = entries.value

    override suspend fun deleteFinished(): Int {
        val protectedIds = savedGameDao?.openHistoryIds().orEmpty()
        val kept = entries.value.filter { it.id in protectedIds }
        val removed = entries.value.size - kept.size
        entries.value = kept
        return removed
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
                )
            }
        }
    }

    override fun observeStartedCount(): Flow<Int> = entries.map { it.size }

    override fun observeCompletedCount(): Flow<Int> = entries.map { list -> list.count { it.completed } }

    override fun observeTotalMistakes(): Flow<Int> =
        entries.map { list -> list.filter { it.completed }.sumOf { it.mistakeCount } }

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
