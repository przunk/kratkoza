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
 * migration cannot invent the game type of a row written before the column existed. Version 2 adds
 * the one thing that could not be foreseen that way, the Daily calendar.
 */
@Database(
    entities = [
        PuzzleEntity::class,
        SavedGameEntity::class,
        GameHistoryEntity::class,
        DailyPuzzleEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class KratkozaDatabase : RoomDatabase() {

    abstract fun puzzleDao(): PuzzleDao

    abstract fun dailyPuzzleDao(): DailyPuzzleDao

    abstract fun savedGameDao(): SavedGameDao

    abstract fun gameHistoryDao(): GameHistoryDao

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

        fun migrations(): Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): KratkozaDatabase =
            Room.databaseBuilder(context.applicationContext, KratkozaDatabase::class.java, NAME)
                .addMigrations(*migrations())
                .build()
    }
}
