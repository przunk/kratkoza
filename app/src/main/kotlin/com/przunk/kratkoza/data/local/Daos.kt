package com.przunk.kratkoza.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(puzzle: PuzzleEntity)

    @Query("SELECT * FROM puzzles WHERE puzzleId = :puzzleId")
    suspend fun byId(puzzleId: String): PuzzleEntity?
}

@Dao
interface SavedGameDao {

    @Upsert
    suspend fun upsert(save: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE puzzleId = :puzzleId")
    suspend fun byId(puzzleId: String): SavedGameEntity?

    @Query("DELETE FROM saved_games WHERE puzzleId = :puzzleId")
    suspend fun delete(puzzleId: String)

    /**
     * Every unfinished game, most recently played first, joined to the board it belongs to.
     *
     * The saved-games list shows a preview of the board (project.md section 22), so it needs the
     * grid itself, not just the metadata — hence the join rather than a second query per row.
     */
    @Query(
        """
        SELECT s.puzzleId AS puzzleId,
               s.startedAt AS startedAt,
               s.lastPlayedAt AS lastPlayedAt,
               s.elapsedMillis AS elapsedMillis,
               s.mistakeCount AS mistakeCount,
               s.board AS board,
               p.difficulty AS difficulty,
               p.gameType AS gameType,
               p.givens AS givens
        FROM saved_games s
        JOIN puzzles p ON p.puzzleId = s.puzzleId
        ORDER BY s.lastPlayedAt DESC
        """,
    )
    fun observeAll(): Flow<List<SavedGameSummaryRow>>

    @Query("SELECT puzzleId FROM saved_games ORDER BY lastPlayedAt DESC LIMIT 1")
    fun observeMostRecentId(): Flow<String?>
}

@Dao
interface GameHistoryDao {

    @Insert
    suspend fun insert(entry: GameHistoryEntity): Long

    @Query(
        """
        UPDATE game_history
        SET finishedAt = :finishedAt,
            elapsedMillis = :elapsedMillis,
            mistakeCount = :mistakeCount,
            completed = :completed
        WHERE id = :id
        """,
    )
    suspend fun markFinished(
        id: Long,
        finishedAt: Long,
        elapsedMillis: Long,
        mistakeCount: Int,
        completed: Boolean,
    )

    /**
     * Wipes the record, except for games still in progress.
     *
     * A save points at the history row opened when its game started, and finishing that game
     * updates that row. Deleting it would leave the save pointing at nothing, and the game would
     * silently fail to be recorded when it was completed. A game still on the board has not
     * finished being played, so its row is not history yet.
     */
    @Query(
        """
        DELETE FROM game_history
        WHERE id NOT IN (SELECT historyId FROM saved_games)
        """,
    )
    suspend fun deleteFinished(): Int

    @Query("SELECT COUNT(*) FROM game_history")
    fun observeStartedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM game_history WHERE completed")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(mistakeCount), 0) FROM game_history WHERE completed")
    fun observeTotalMistakes(): Flow<Int>

    /**
     * Per-level totals, in one pass.
     *
     * Started games are counted over every row; times and mistakes only over completed ones. An
     * abandoned game has no meaningful duration, so averaging it in would drag every level's
     * average toward whatever the player happened to quit at. Levels with nothing completed yet
     * come back with nulls rather than zeros — "no best time" and "best time 0" are not the same
     * statement.
     */
    @Query(
        """
        SELECT difficulty AS difficulty,
               COUNT(*) AS startedCount,
               SUM(CASE WHEN completed THEN 1 ELSE 0 END) AS completedCount,
               MIN(CASE WHEN completed THEN elapsedMillis END) AS bestTimeMillis,
               AVG(CASE WHEN completed THEN elapsedMillis END) AS averageTimeMillis,
               AVG(CASE WHEN completed THEN CAST(mistakeCount AS REAL) END) AS averageMistakes
        FROM game_history
        GROUP BY difficulty
        """,
    )
    fun observeByDifficulty(): Flow<List<DifficultyStatisticsRow>>

}

/** Projection for the saved-games list. */
data class SavedGameSummaryRow(
    val puzzleId: String,
    val startedAt: Long,
    val lastPlayedAt: Long,
    val elapsedMillis: Long,
    val mistakeCount: Int,
    val board: String,
    val difficulty: String,
    val gameType: String,
    val givens: String,
)

/** Projection for per-level statistics; the aggregates are null until something is completed. */
data class DifficultyStatisticsRow(
    val difficulty: String,
    val startedCount: Int,
    val completedCount: Int,
    val bestTimeMillis: Long?,
    val averageTimeMillis: Double?,
    val averageMistakes: Double?,
)
