package com.przunk.kratkoza.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.model.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The saved-games queries against a real database — the join and the ordering in particular. */
@RunWith(AndroidJUnit4::class)
class SavedGameDaoTest {

    private lateinit var database: KratkozaDatabase
    private lateinit var saves: SavedGameDao
    private lateinit var puzzles: PuzzleDao
    private lateinit var history: GameHistoryDao

    @Before
    fun setUp() {
        database = createTestDatabase()
        saves = database.savedGameDao()
        puzzles = database.puzzleDao()
        history = database.gameHistoryDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the list joins in what the preview needs and orders by last played`() = runTest {
        puzzles.insert(puzzleEntity("older", Difficulty.EASY))
        puzzles.insert(puzzleEntity("newer", Difficulty.MASTER))
        saves.upsert(savedGameEntity("older", historyId = 1, lastPlayedAt = 1_000))
        saves.upsert(savedGameEntity("newer", historyId = 2, lastPlayedAt = 9_000))

        val rows = saves.observeAll().first()

        assertEquals(listOf("newer", "older"), rows.map { it.puzzleId })
        // The difficulty and the givens come from the puzzles table; the list cannot draw its
        // preview without them, and a join that silently dropped them would leave it blank.
        assertEquals(Difficulty.MASTER.name, rows.first().difficulty)
        assertTrue(rows.first().givens.isNotEmpty())
    }

    @Test
    fun `a save whose puzzle is missing is left out rather than crashing the list`() = runTest {
        // An inner join is doing this. Worth pinning: switching it to a left join later would put
        // rows with no board into a list that assumes every row has one.
        puzzles.insert(puzzleEntity("present"))
        saves.upsert(savedGameEntity("present", historyId = 1))

        assertEquals(1, saves.observeAll().first().size)
    }

    @Test
    fun `the most recent id follows the clock, not the insertion order`() = runTest {
        puzzles.insert(puzzleEntity("first"))
        puzzles.insert(puzzleEntity("second"))
        saves.upsert(savedGameEntity("first", historyId = 1, lastPlayedAt = 8_000))
        saves.upsert(savedGameEntity("second", historyId = 2, lastPlayedAt = 3_000))

        assertEquals("first", saves.observeMostRecentId().first())
    }

    @Test
    fun `nothing saved means nothing to continue`() = runTest {
        assertNull(saves.observeMostRecentId().first())
        assertTrue(saves.observeAll().first().isEmpty())
    }

    @Test
    fun `upsert replaces the save rather than adding a second one for the same board`() = runTest {
        // One save per board is the rule the resume flow depends on; two rows would make "continue"
        // ambiguous.
        puzzles.insert(puzzleEntity("a"))
        saves.upsert(savedGameEntity("a", historyId = 1, elapsedMillis = 1_000))
        saves.upsert(savedGameEntity("a", historyId = 1, elapsedMillis = 60_000))

        val rows = saves.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(60_000L, rows.single().elapsedMillis)
    }

    @Test
    fun `deleting a puzzle takes its save with it`() = runTest {
        // The foreign key declares ON DELETE CASCADE. SQLite ignores foreign keys unless they are
        // switched on, so this checks that Room actually did — a save pointing at a deleted board
        // is a game that cannot be opened.
        puzzles.insert(puzzleEntity("doomed"))
        saves.upsert(savedGameEntity("doomed", historyId = 1))
        assertNotNull(saves.byId("doomed"))

        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM puzzles WHERE puzzleId = ?",
            arrayOf("doomed"),
        )

        assertNull(saves.byId("doomed"))
    }

    @Test
    fun `inserting the same puzzle twice keeps the first`() = runTest {
        // OnConflictStrategy.IGNORE. Generating a board the player has met before must not rewrite
        // the stored one, because saves and history rows already point at it.
        puzzles.insert(puzzleEntity("stable", Difficulty.EASY))
        puzzles.insert(puzzleEntity("stable", Difficulty.MASTER))

        assertEquals(Difficulty.EASY.name, puzzles.byId("stable")?.difficulty)
    }

    @Test
    fun `games still on the board are counted per difficulty`() = runTest {
        puzzles.insert(puzzleEntity("easy-1", Difficulty.EASY))
        puzzles.insert(puzzleEntity("easy-2", Difficulty.EASY))
        puzzles.insert(puzzleEntity("master-1", Difficulty.MASTER))
        saves.upsert(savedGameEntity("easy-1", historyId = 1))
        saves.upsert(savedGameEntity("easy-2", historyId = 2))
        saves.upsert(savedGameEntity("master-1", historyId = 3))

        val counts = saves.observeInProgressByDifficulty().first().associateBy { it.difficulty }

        assertEquals(2, counts.getValue(Difficulty.EASY.name).inProgressCount)
        assertEquals(1, counts.getValue(Difficulty.MASTER.name).inProgressCount)
        assertNull("a level with nothing waiting has no row", counts[Difficulty.HARD.name])
    }

    @Test
    fun `an abandoned game stops being counted as in progress`() = runTest {
        // The distinction this query exists for: history remembers the game was started, but with
        // the save gone there is no board left, so it cannot be described as partly filled.
        puzzles.insert(puzzleEntity("abandoned", Difficulty.HARD))
        history.insert(historyEntity("abandoned", Difficulty.HARD, completed = false))
        saves.upsert(savedGameEntity("abandoned", historyId = 1))
        assertEquals(1, saves.observeInProgressByDifficulty().first().size)

        saves.delete("abandoned")

        assertTrue(saves.observeInProgressByDifficulty().first().isEmpty())
        assertEquals("but it is still a started game", 1, history.observeStartedCount().first())
    }
}
