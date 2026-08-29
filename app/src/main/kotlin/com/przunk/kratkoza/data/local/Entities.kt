package com.przunk.kratkoza.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A board, stored once and referred to by everything else (project.md section 6).
 *
 * Enums and grids are kept as text rather than as ordinals or blobs: an ordinal breaks silently the
 * day an enum entry is inserted in the middle, and a stored grid that reads as `53..7....` in a
 * database browser is worth a great deal when a save has gone wrong.
 */
@Entity(tableName = "puzzles")
data class PuzzleEntity(
    @PrimaryKey val puzzleId: String,
    val generatorVersion: Int,
    /** [com.przunk.kratkoza.domain.model.Difficulty] by name. */
    val difficulty: String,
    /** [com.przunk.kratkoza.domain.model.GameType] by name. */
    val gameType: String,
    /** 81 characters, `.` for empty. */
    val givens: String,
    /** 81 characters, always complete. */
    val solution: String,
    /** Set when the board came from a deterministic generator run. */
    val seed: Long?,
)

/**
 * Which board is a given day's Daily at a given level (project.md section 26).
 *
 * The board itself is derived, not stored — the same date and level always generate the same grid.
 * What this table records is that *this device already generated it*, so an app update that changes
 * the generator cannot swap a puzzle out from under a game in progress.
 *
 * [rulesetVersion] travels with the row for the same reason: a row written under an older recipe
 * describes a board today's code would no longer produce, and pretending otherwise would silently
 * corrupt the day's identity.
 */
@Entity(
    tableName = "daily_puzzles",
    primaryKeys = ["date", "difficulty", "rulesetVersion"],
    foreignKeys = [
        ForeignKey(
            entity = PuzzleEntity::class,
            parentColumns = ["puzzleId"],
            childColumns = ["puzzleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("puzzleId")],
)
data class DailyPuzzleEntity(
    /** ISO date of the Daily day, yyyy-MM-dd, as decided by the 08:00 Warsaw changeover. */
    val date: String,
    /** [com.przunk.kratkoza.domain.model.Difficulty] by name. */
    val difficulty: String,
    val puzzleId: String,
    val rulesetVersion: Int,
)

/**
 * A game in progress (project.md section 22).
 *
 * One save per board: resuming the same puzzle continues the same game rather than starting a
 * second one beside it.
 *
 * [historyId] ties the save to the row opened when the game started, so a game that is finished —
 * or abandoned — updates the record it began rather than creating a new one.
 */
@Entity(
    tableName = "saved_games",
    foreignKeys = [
        ForeignKey(
            entity = PuzzleEntity::class,
            parentColumns = ["puzzleId"],
            childColumns = ["puzzleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lastPlayedAt"), Index("historyId")],
)
data class SavedGameEntity(
    @PrimaryKey val puzzleId: String,
    val historyId: Long,
    val startedAt: Long,
    val lastPlayedAt: Long,
    /** Time actually spent playing, with pauses excluded (project.md section 20). */
    val elapsedMillis: Long,
    /** The board as it stands: givens plus what the player has entered. */
    val board: String,
    /** Pencil marks, three hex characters per cell. */
    val candidates: String,
    /** The undo stack, see [MoveHistoryCodec]. */
    val undoHistory: String,
    val mistakeCount: Int,
    /**
     * Whether this game still counts for a public ranking (project.md section 28). Always false for
     * solo games; the column exists so that Daily does not need a migration to arrive.
     */
    val rankedEligible: Boolean,
)

/**
 * One played game (project.md section 23).
 *
 * A row is written when a game *starts*, not when it ends, so that project.md section 33's "games
 * started" and "completion rate" have something to count. An unfinished row is a game that was
 * started and not completed.
 *
 * Rows are never rewritten by later rule changes: [rulesetVersion] records which rules produced
 * them, so statistics can stay honest across versions.
 */
@Entity(
    tableName = "game_history",
    indices = [Index("puzzleId"), Index("startedAt"), Index("completed")],
)
data class GameHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleId: String,
    val gameType: String,
    val difficulty: String,
    val startedAt: Long,
    /** `null` while the game is still open. */
    val finishedAt: Long?,
    val elapsedMillis: Long,
    val mistakeCount: Int,
    val completed: Boolean,
    /**
     * project.md section 23 wants a score kept. The algorithm is deliberately open (section 39), so
     * the column exists and stays `null` until it is agreed.
     */
    val score: Int?,
    /** project.md section 32. The formula is open (section 39), so this stays `0` for now. */
    val xpEarned: Int,
    /** project.md section 27; nothing is publicly ranked yet. */
    val publiclyRanked: Boolean,
    /**
     * Whether the game still counted for a ranking when it ended (project.md section 28).
     *
     * A different question from [publiclyRanked], and conflating the two was a bug: eligibility is
     * about how the game was played, submission about whether a leaderboard ever saw it. Nothing
     * submits yet, so [publiclyRanked] is always false — reading it as "did this count" reported
     * every finished Daily as out of the ranking.
     */
    val rankedEligible: Boolean,
    /**
     * Assist settings that change how a result should be read (project.md section 23). Only the
     * settings that exist today are recorded.
     */
    val autoRemoveCandidates: Boolean,
    val rulesetVersion: Int,
)
