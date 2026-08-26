package com.przunk.kratkoza.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.model.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The version 1 to version 2 migration, run against a real database.
 *
 * A migration is the one piece of this app that cannot be fixed after the fact: it meets a phone
 * that already holds somebody's half-solved games, exactly once, and a mistake there loses them.
 * Compiling proves nothing — Room only compares the migrated schema against what it expects when it
 * opens the database, and only then does a wrong `CREATE TABLE` surface.
 *
 * So this builds a genuine version 1 database from the exported schema, puts a game in it, migrates,
 * and checks both that Room accepts the result and that the game is still there.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val name = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() = context.deleteDatabase(name).let { }

    /** The schema exactly as version 1 shipped it, taken from `app/schemas/…/1.json`. */
    private val versionOneDdl = listOf(
        "CREATE TABLE IF NOT EXISTS `puzzles` (`puzzleId` TEXT NOT NULL, `generatorVersion` INTEGER NOT NULL, `difficulty` TEXT NOT NULL, `gameType` TEXT NOT NULL, `givens` TEXT NOT NULL, `solution` TEXT NOT NULL, `seed` INTEGER, PRIMARY KEY(`puzzleId`))",
        "CREATE TABLE IF NOT EXISTS `saved_games` (`puzzleId` TEXT NOT NULL, `historyId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `elapsedMillis` INTEGER NOT NULL, `board` TEXT NOT NULL, `candidates` TEXT NOT NULL, `undoHistory` TEXT NOT NULL, `mistakeCount` INTEGER NOT NULL, `rankedEligible` INTEGER NOT NULL, PRIMARY KEY(`puzzleId`), FOREIGN KEY(`puzzleId`) REFERENCES `puzzles`(`puzzleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_saved_games_lastPlayedAt` ON `saved_games` (`lastPlayedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_saved_games_historyId` ON `saved_games` (`historyId`)",
        "CREATE TABLE IF NOT EXISTS `game_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `puzzleId` TEXT NOT NULL, `gameType` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `elapsedMillis` INTEGER NOT NULL, `mistakeCount` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `score` INTEGER, `xpEarned` INTEGER NOT NULL, `publiclyRanked` INTEGER NOT NULL, `autoRemoveCandidates` INTEGER NOT NULL, `rulesetVersion` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_game_history_puzzleId` ON `game_history` (`puzzleId`)",
        "CREATE INDEX IF NOT EXISTS `index_game_history_startedAt` ON `game_history` (`startedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_game_history_completed` ON `game_history` (`completed`)",
    )

    private fun createVersionOneDatabase(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    versionOneDdl.forEach(db::execSQL)
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS room_master_table " +
                            "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun `a version one database migrates and keeps the game that was in it`() = runTest {
        createVersionOneDatabase().use { db ->
            db.execSQL(
                "INSERT INTO puzzles VALUES ('p1', 1, ?, 'SOLO', ?, ?, NULL)",
                arrayOf(Difficulty.HARD.name, ".".repeat(81), "1".repeat(81)),
            )
            db.execSQL(
                "INSERT INTO saved_games VALUES ('p1', 7, 0, 0, 1234, ?, ?, '', 2, 0)",
                arrayOf(".".repeat(81), "000".repeat(81)),
            )
            db.version = 1
        }

        // Room validates the migrated schema against the entities as it opens; a malformed
        // CREATE TABLE fails here rather than on somebody's phone.
        val database = Room.databaseBuilder(context, KratkozaDatabase::class.java, name)
            .addMigrations(*KratkozaDatabase.migrations())
            .build()

        try {
            val save = database.savedGameDao().byId("p1")
            assertNotNull("the game in progress must survive the migration", save)
            assertEquals(1234L, save!!.elapsedMillis)
            assertEquals(2, save.mistakeCount)
            assertNotNull(database.puzzleDao().byId("p1"))

            // And the new table has to be usable, not merely present.
            database.dailyPuzzleDao().insert(
                listOf(DailyPuzzleEntity("2026-08-26", Difficulty.EASY.name, "p1", 1)),
            )
            assertEquals(1, database.dailyPuzzleDao().forDate("2026-08-26", 1).size)
        } finally {
            database.close()
        }
    }
}
