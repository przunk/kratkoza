package com.przunk.kratkoza.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The app's local store (technical_requirements.md section 6).
 *
 * Version 1 already carried the columns Daily and public ranking would need — game type, ranked
 * eligibility, score, XP, ruleset version. They were cheap to carry and expensive to retrofit: a
 * migration cannot invent the game type of a row written before the column existed. Later versions
 * add the Daily calendar, ranking eligibility, a versioned Daily key, the solved-board archive and
 * assistance usage.
 */
@Database(
    entities = [
        PuzzleEntity::class,
        SavedGameEntity::class,
        GameHistoryEntity::class,
        DailyPuzzleEntity::class,
        SolvedBoardEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class KratkozaDatabase : RoomDatabase() {

    abstract fun puzzleDao(): PuzzleDao

    abstract fun dailyPuzzleDao(): DailyPuzzleDao

    abstract fun savedGameDao(): SavedGameDao

    abstract fun gameHistoryDao(): GameHistoryDao

    abstract fun solvedBoardDao(): SolvedBoardDao

    companion object {
        private const val NAME = "kratkoza.db"

        /**
         * Adds the Daily calendar table.
         *
         * Written out rather than falling back to a destructive migration: the app is already on a
         * phone with games in progress, and wiping somebody's half-solved board to make room for a
         * table beside it would be an absurd trade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_puzzles` (" +
                        "`date` TEXT NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, " +
                        "`puzzleId` TEXT NOT NULL, " +
                        "`rulesetVersion` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`date`, `difficulty`), " +
                        "FOREIGN KEY(`puzzleId`) REFERENCES `puzzles`(`puzzleId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_puzzles_puzzleId` " +
                        "ON `daily_puzzles` (`puzzleId`)",
                )
            }
        }

        /** Exposed so the migration test can run exactly what ships. */
        /**
         * Records whether a finished game still counted for a ranking.
         *
         * `publiclyRanked` was doing double duty and doing it wrongly: it says whether a leaderboard
         * ever saw the result, which is always false while no leaderboard exists, so every finished
         * Daily reported itself as out of the ranking. Existing rows default to 0, which is true of
         * every solo game and honest about the Dailies played before the column existed.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `game_history` ADD COLUMN `rankedEligible` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Puts `rulesetVersion` into the key of the Daily calendar.
         *
         * The recipe for a Daily board is allowed to change — that is the entire purpose of
         * `RULESET_VERSION` — and on the day of such an update the normal case is a phone that has
         * already opened today under the old recipe. The key was `(date, difficulty)`, so the new
         * day's Easy and Hard collided with the old day's Easy and Hard and were dropped on insert,
         * while a difficulty the old recipe had not used landed unopposed. The player was left
         * looking at a single board.
         *
         * A board's identity is the day, the difficulty *and* the recipe it was made under. The old
         * rows are kept: nothing reads them any more, and deleting somebody's history to tidy up
         * would be a poor trade for a few bytes.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_puzzles_new` (" +
                        "`date` TEXT NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, " +
                        "`puzzleId` TEXT NOT NULL, " +
                        "`rulesetVersion` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`date`, `difficulty`, `rulesetVersion`), " +
                        "FOREIGN KEY(`puzzleId`) REFERENCES `puzzles`(`puzzleId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL(
                    "INSERT INTO `daily_puzzles_new` " +
                        "(`date`, `difficulty`, `puzzleId`, `rulesetVersion`) " +
                        "SELECT `date`, `difficulty`, `puzzleId`, `rulesetVersion` " +
                        "FROM `daily_puzzles`",
                )
                connection.execSQL("DROP TABLE `daily_puzzles`")
                connection.execSQL("ALTER TABLE `daily_puzzles_new` RENAME TO `daily_puzzles`")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_puzzles_puzzleId` " +
                        "ON `daily_puzzles` (`puzzleId`)",
                )
            }
        }

        /**
         * Adds the solved-board archive and backfills successful Solo games already on the phone.
         *
         * The archive is deliberately separate from `game_history`: resetting statistics deletes
         * that table, while the owner explicitly wants the visual record of solved boards to stay.
         * Backfilling makes the first History screen truthful on upgrade rather than starting the
         * player's past at the day this feature shipped.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `solved_boards` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`puzzleId` TEXT NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`elapsedMillis` INTEGER NOT NULL, " +
                        "`mistakeCount` INTEGER NOT NULL, " +
                        "`xpEarned` INTEGER NOT NULL, " +
                        "`score` INTEGER, " +
                        "FOREIGN KEY(`puzzleId`) REFERENCES `puzzles`(`puzzleId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_solved_boards_puzzleId` " +
                        "ON `solved_boards` (`puzzleId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_solved_boards_completedAt` " +
                        "ON `solved_boards` (`completedAt`)",
                )
                connection.execSQL(
                    "INSERT INTO `solved_boards` " +
                        "(`puzzleId`, `completedAt`, `elapsedMillis`, `mistakeCount`, " +
                        "`xpEarned`, `score`) " +
                        "SELECT h.`puzzleId`, h.`finishedAt`, h.`elapsedMillis`, h.`mistakeCount`, " +
                        "h.`xpEarned`, h.`score` FROM `game_history` h " +
                        "JOIN `puzzles` p ON p.`puzzleId` = h.`puzzleId` " +
                        "WHERE h.`gameType` = 'SOLO' AND h.`completed` = 1 " +
                        "AND h.`finishedAt` IS NOT NULL",
                )
            }
        }

        /**
         * Records explicit hint use in every place an attempt can live.
         *
         * Version 6 cannot be used for the released schema: an abandoned assumption-tools build
         * already wrote a different schema under that number to at least one test phone. Moving
         * directly from the released version 5 to 7 avoids a same-version identity mismatch that
         * Room cannot migrate.
         */
        private val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `saved_games` ADD COLUMN `hintCount` INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    "ALTER TABLE `game_history` ADD COLUMN `hintCount` INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    "ALTER TABLE `solved_boards` ADD COLUMN `hintCount` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Recovers phones that ran the abandoned schema numbered 6.
         *
         * Its three assumption-only save columns never shipped and have no meaning in the current
         * game. Rebuilding the save table keeps every supported field, discards only that orphaned
         * state, and adds the hint counter expected by the official version 7 schema.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_games_new` (" +
                        "`puzzleId` TEXT NOT NULL, " +
                        "`historyId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`lastPlayedAt` INTEGER NOT NULL, " +
                        "`elapsedMillis` INTEGER NOT NULL, " +
                        "`board` TEXT NOT NULL, " +
                        "`candidates` TEXT NOT NULL, " +
                        "`undoHistory` TEXT NOT NULL, " +
                        "`mistakeCount` INTEGER NOT NULL, " +
                        "`hintCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`rankedEligible` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`puzzleId`), " +
                        "FOREIGN KEY(`puzzleId`) REFERENCES `puzzles`(`puzzleId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL(
                    "INSERT INTO `saved_games_new` (" +
                        "`puzzleId`, `historyId`, `startedAt`, `lastPlayedAt`, `elapsedMillis`, " +
                        "`board`, `candidates`, `undoHistory`, `mistakeCount`, `hintCount`, " +
                        "`rankedEligible`) " +
                        "SELECT `puzzleId`, `historyId`, `startedAt`, `lastPlayedAt`, " +
                        "`elapsedMillis`, `board`, `candidates`, `undoHistory`, `mistakeCount`, 0, " +
                        "`rankedEligible` FROM `saved_games`",
                )
                connection.execSQL("DROP TABLE `saved_games`")
                connection.execSQL("ALTER TABLE `saved_games_new` RENAME TO `saved_games`")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_games_lastPlayedAt` " +
                        "ON `saved_games` (`lastPlayedAt`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_games_historyId` " +
                        "ON `saved_games` (`historyId`)",
                )
                connection.execSQL(
                    "ALTER TABLE `game_history` ADD COLUMN `hintCount` INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    "ALTER TABLE `solved_boards` ADD COLUMN `hintCount` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun migrations(): Array<Migration> =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_7,
                MIGRATION_6_7,
            )

        /**
         * [name] is a parameter so tests open the database exactly as the app does. A configuration
         * that only production uses is a configuration nothing checks.
         *
         * Going *down* a version wipes the database rather than refusing to open it. Room's default
         * there is to throw, and it throws on every launch for ever, so the player is left with an
         * app that cannot start until they clear its data by hand — which loses the same games the
         * wipe would, and leaves them to work out why. Play never installs an older build over a
         * newer one, so this is reached only by sideloading, which is what testing this app is made
         * of; it happened here when a build from an abandoned branch left a phone holding a schema
         * numbered above anything that would ever ship.
         */
        fun create(context: Context, name: String = NAME): KratkozaDatabase =
            Room.databaseBuilder(context.applicationContext, KratkozaDatabase::class.java, name)
                .addMigrations(*migrations())
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
