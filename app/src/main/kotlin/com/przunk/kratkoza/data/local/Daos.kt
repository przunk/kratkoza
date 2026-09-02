package com.przunk.kratkoza.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
interface DailyPuzzleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entries: List<DailyPuzzleEntity>)

    @Query("SELECT * FROM daily_puzzles WHERE date = :date AND rulesetVersion = :rulesetVersion")
    suspend fun forDate(date: String, rulesetVersion: Int): List<DailyPuzzleEntity>

    /** Which day a board belongs to, so a finished game can be totalled against the right one. */
    @Query(
        "SELECT date FROM daily_puzzles WHERE puzzleId = :puzzleId AND rulesetVersion = :rulesetVersion LIMIT 1",
    )
    suspend fun dateOf(puzzleId: String, rulesetVersion: Int): String?

    /**
     * The cycle a saved Daily belongs to, including boards made by an older recipe.
     *
     * A save can outlive an app update that bumps the ruleset. Filtering by today's version would
     * then make its ranking deadline disappear and let the stale attempt remain eligible forever.
     */
    @Query(
        "SELECT date FROM daily_puzzles WHERE puzzleId = :puzzleId " +
            "ORDER BY rulesetVersion DESC LIMIT 1",
    )
    suspend fun dateOfPuzzle(puzzleId: String): String?
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
               p.givens AS givens,
               p.solution AS solution,
               p.generatorVersion AS generatorVersion
        FROM saved_games s
        JOIN puzzles p ON p.puzzleId = s.puzzleId
        ORDER BY s.lastPlayedAt DESC
        """,
    )
    fun observeAll(): Flow<List<SavedGameSummaryRow>>

    @Query("SELECT puzzleId FROM saved_games ORDER BY lastPlayedAt DESC LIMIT 1")
    fun observeMostRecentId(): Flow<String?>

    /**
     * How many games are still on the board, per difficulty.
     *
     * Counted from the saves rather than from `game_history`, and the difference matters: history
     * knows a game was started and not finished, but not whether it is still playable. A game the
     * player abandoned from the saved-games list is unfinished forever and has no board left to
     * describe, so counting it here would promise a progress figure that cannot be computed.
     */
    @Query(
        """
        SELECT p.difficulty AS difficulty, COUNT(*) AS inProgressCount
        FROM saved_games s
        JOIN puzzles p ON p.puzzleId = s.puzzleId
        GROUP BY p.difficulty
        """,
    )
    fun observeInProgressByDifficulty(): Flow<List<InProgressCountRow>>
}

@Dao
interface SolvedBoardDao {

    @Insert
    suspend fun insert(entry: SolvedBoardEntity): Long

    /**
     * Every successful Solo completion, newest first, with the original board and replay state.
     *
     * `completionCount` deliberately counts attempts rather than distinct boards: each row is one
     * memory, while the count says how often this particular puzzle has been beaten. The save join
     * lets the detail action say Continue instead of offering to overwrite an existing replay.
     */
    @Query(
        """
        SELECT a.id AS id,
               a.puzzleId AS puzzleId,
               a.completedAt AS completedAt,
               a.elapsedMillis AS elapsedMillis,
               a.mistakeCount AS mistakeCount,
               a.xpEarned AS xpEarned,
               a.score AS score,
               p.difficulty AS difficulty,
               p.givens AS givens,
               p.generatorVersion AS generatorVersion,
               p.seed AS seed,
               (SELECT COUNT(*) FROM solved_boards counted
                WHERE counted.puzzleId = a.puzzleId) AS completionCount,
               EXISTS(SELECT 1 FROM saved_games s
                      WHERE s.puzzleId = a.puzzleId) AS inProgress
        FROM solved_boards a
        JOIN puzzles p ON p.puzzleId = a.puzzleId
        ORDER BY a.completedAt DESC, a.id DESC
        """,
    )
    fun observeAll(): Flow<List<SolvedBoardRow>>

    @Query(
        """
        SELECT a.id AS id,
               a.puzzleId AS puzzleId,
               a.completedAt AS completedAt,
               a.elapsedMillis AS elapsedMillis,
               a.mistakeCount AS mistakeCount,
               a.xpEarned AS xpEarned,
               a.score AS score,
               p.difficulty AS difficulty,
               p.givens AS givens,
               p.generatorVersion AS generatorVersion,
               p.seed AS seed,
               (SELECT COUNT(*) FROM solved_boards counted
                WHERE counted.puzzleId = a.puzzleId) AS completionCount,
               EXISTS(SELECT 1 FROM saved_games s
                      WHERE s.puzzleId = a.puzzleId) AS inProgress
        FROM solved_boards a
        JOIN puzzles p ON p.puzzleId = a.puzzleId
        WHERE a.id = :id
        """,
    )
    fun observeById(id: Long): Flow<SolvedBoardRow?>
}

@Dao
abstract class GameHistoryDao {

    @Insert
    abstract suspend fun insert(entry: GameHistoryEntity): Long

    @Query(
        """
        UPDATE game_history
        SET finishedAt = :finishedAt,
            elapsedMillis = :elapsedMillis,
            mistakeCount = :mistakeCount,
            completed = :completed,
            xpEarned = :xpEarned,
            rankedEligible = :rankedEligible,
            score = :score
        WHERE id = :id
        """,
    )
    abstract suspend fun markFinished(
        id: Long,
        finishedAt: Long,
        elapsedMillis: Long,
        mistakeCount: Int,
        completed: Boolean,
        xpEarned: Int,
        rankedEligible: Boolean,
        score: Int?,
    )

    /**
     * The scored Daily total across a set of boards — one day's worth, in practice.
     *
     * Null when none of them is scored, which is not the same as zero: an unscored day has no total
     * rather than a total of nothing.
     */
    @Query(
        """
        SELECT SUM(score) FROM game_history
        WHERE gameType = 'DAILY' AND completed AND puzzleId IN (:puzzleIds)
        """,
    )
    abstract suspend fun dailyScoreSum(puzzleIds: List<String>): Long?

    /** Every Daily ever finished, added together. */
    @Query("SELECT SUM(score) FROM game_history WHERE gameType = 'DAILY' AND completed")
    abstract suspend fun allTimeDailyScoreSum(): Long?

    /** Marks a finished result as one the public ranking accepted. */
    @Query("UPDATE game_history SET publiclyRanked = 1 WHERE id = :id")
    abstract suspend fun markPubliclyRanked(id: Long)

    @Query("DELETE FROM game_history")
    protected abstract suspend fun deleteAllHistory()

    /**
     * Cuts every save loose from its history row.
     *
     * [SavedGameEntity.historyId] `0` means "not recorded yet" — Room's generated ids start at 1, so
     * the value can never collide with a real row.
     */
    @Query("UPDATE saved_games SET historyId = 0")
    protected abstract suspend fun detachSavesFromHistory()

    /**
     * Clears the player's whole record.
     *
     * Games still on the board survive; only their link to the old record is cut, and finishing one
     * afterwards opens a fresh row (see [com.przunk.kratkoza.data.repository.GameRepository]).
     *
     * An earlier version kept the history rows of games in progress, on the grounds that a game
     * still being played is not history yet. That was defensible and wrong: it left "games started"
     * showing whatever was unfinished, so a reset looked like it had done nothing. A reset means
     * zero.
     *
     * One transaction, because a deleted row with saves still pointing at it is exactly the
     * dangling reference this is meant to avoid.
     */
    @Transaction
    open suspend fun resetStatistics() {
        deleteAllHistory()
        detachSavesFromHistory()
    }

    /**
     * Every recorded attempt at these boards.
     *
     * A board can have more than one row — abandoned and started again — so the caller decides what
     * to make of them rather than the query guessing.
     */
    @Query("SELECT * FROM game_history WHERE puzzleId IN (:puzzleIds)")
    abstract suspend fun attemptsOn(puzzleIds: List<String>): List<GameHistoryEntity>

    @Query("SELECT COUNT(*) FROM game_history")
    abstract fun observeStartedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM game_history WHERE completed")
    abstract fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(mistakeCount), 0) FROM game_history WHERE completed")
    abstract fun observeTotalMistakes(): Flow<Int>

    /**
     * Every XP the player has earned (project.md section 32).
     *
     * Summed from history rather than kept as a running total, like every other statistic here: a
     * stored total that has drifted from the rows behind it cannot be repaired, and this one would
     * carry the player's level with it.
     */
    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM game_history")
    abstract fun observeTotalXp(): Flow<Long>

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
    abstract fun observeByDifficulty(): Flow<List<DifficultyStatisticsRow>>

    /**
     * The scored Daily record, without unfinished attempts or solo games.
     *
     * Score aggregates deliberately remain nullable. A completed row from before scoring existed
     * is not a zero-point result, and SQLite's handling of `NULL` preserves that distinction.
     */
    @Query(
        """
        SELECT COUNT(*) AS completedCount,
               SUM(score) AS totalScore,
               MAX(score) AS bestScore,
               AVG(score) AS averageScore,
               COUNT(CASE WHEN rankedEligible THEN 1 END) AS rankedEligibleCount
        FROM game_history
        WHERE gameType = 'DAILY' AND completed
        """,
    )
    abstract fun observeDailySummary(): Flow<DailyStatisticsSummaryRow>

    /** Completed Daily boards per level; absent levels do not produce empty rows. */
    @Query(
        """
        SELECT difficulty AS difficulty, COUNT(*) AS completedCount
        FROM game_history
        WHERE gameType = 'DAILY' AND completed
        GROUP BY difficulty
        """,
    )
    abstract fun observeDailyByDifficulty(): Flow<List<DailyDifficultyStatisticsRow>>

}

/** Projection for the count of games still on the board, per difficulty. */
data class InProgressCountRow(
    val difficulty: String,
    val inProgressCount: Int,
)

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
    val solution: String,
    val generatorVersion: Int,
)

/** Projection shared by the archive list and one completion's detail screen. */
data class SolvedBoardRow(
    val id: Long,
    val puzzleId: String,
    val completedAt: Long,
    val elapsedMillis: Long,
    val mistakeCount: Int,
    val xpEarned: Int,
    val score: Int?,
    val difficulty: String,
    val givens: String,
    val generatorVersion: Int,
    val seed: Long?,
    val completionCount: Int,
    val inProgress: Boolean,
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

/** Overall Daily aggregates; scores stay null until at least one scored completion exists. */
data class DailyStatisticsSummaryRow(
    val completedCount: Int,
    val totalScore: Long?,
    val bestScore: Int?,
    val averageScore: Double?,
    val rankedEligibleCount: Int,
)

/** Projection for completed Daily boards at one level. */
data class DailyDifficultyStatisticsRow(
    val difficulty: String,
    val completedCount: Int,
)
