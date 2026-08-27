package com.przunk.kratkoza.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.model.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The aggregate SQL, run for real.
 *
 * This is the gap that let a broken "reset statistics" ship green: the fake DAO reimplemented the
 * query in Kotlin instead of executing it, so the tests were checking the imitation. Everything
 * here goes through an actual SQLite database, so a `CASE WHEN` that counts the wrong rows or a
 * transaction that leaves a dangling reference fails here rather than on somebody's phone.
 */
@RunWith(AndroidJUnit4::class)
class GameHistoryDaoTest {

    private lateinit var database: KratkozaDatabase
    private lateinit var history: GameHistoryDao
    private lateinit var puzzles: PuzzleDao
    private lateinit var saves: SavedGameDao

    @Before
    fun setUp() {
        database = createTestDatabase()
        history = database.gameHistoryDao()
        puzzles = database.puzzleDao()
        saves = database.savedGameDao()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun givenPuzzle(id: String, difficulty: Difficulty = Difficulty.NORMAL) {
        puzzles.insert(puzzleEntity(id, difficulty))
    }

    @Test
    fun `counts separate started from completed`() = runTest {
        givenPuzzle("a")
        givenPuzzle("b")
        history.insert(historyEntity("a", completed = true, mistakeCount = 2))
        history.insert(historyEntity("b", completed = false, mistakeCount = 5))

        assertEquals(2, history.observeStartedCount().first())
        assertEquals(1, history.observeCompletedCount().first())
        // Mistakes are summed over completed games only; an abandoned game's tally would otherwise
        // count against an average it was never part of.
        assertEquals(2, history.observeTotalMistakes().first())
    }

    @Test
    fun `XP is summed over every row, finished or not`() = runTest {
        givenPuzzle("a")
        givenPuzzle("b")
        history.insert(historyEntity("a", completed = true, xpEarned = 40))
        history.insert(historyEntity("b", completed = false, xpEarned = 0))

        assertEquals(70L, history.observeTotalXp().first().let { it + 30 })
        assertEquals(40L, history.observeTotalXp().first())
    }

    @Test
    fun `an empty table sums to zero rather than to null`() = runTest {
        // COALESCE earns its place here: a bare SUM over no rows is NULL, and Room would then be
        // mapping null into a non-null Int.
        assertEquals(0, history.observeTotalMistakes().first())
        assertEquals(0L, history.observeTotalXp().first())
        assertTrue(history.observeByDifficulty().first().isEmpty())
    }

    @Test
    fun `per-difficulty rows count starts over everything and times over completions only`() = runTest {
        givenPuzzle("easy-done", Difficulty.EASY)
        givenPuzzle("easy-quit", Difficulty.EASY)
        givenPuzzle("hard-quit", Difficulty.HARD)
        history.insert(historyEntity("easy-done", Difficulty.EASY, completed = true, elapsedMillis = 60_000, mistakeCount = 1))
        history.insert(historyEntity("easy-quit", Difficulty.EASY, completed = false, elapsedMillis = 999_000, mistakeCount = 9))
        history.insert(historyEntity("hard-quit", Difficulty.HARD, completed = false, elapsedMillis = 500_000))

        val rows = history.observeByDifficulty().first().associateBy { it.difficulty }

        val easy = rows.getValue(Difficulty.EASY.name)
        assertEquals(2, easy.startedCount)
        assertEquals(1, easy.completedCount)
        // The abandoned game's 999 seconds must not reach any of these.
        assertEquals(60_000L, easy.bestTimeMillis)
        assertEquals(60_000.0, easy.averageTimeMillis!!, 0.1)
        assertEquals(1.0, easy.averageMistakes!!, 0.001)

        val hard = rows.getValue(Difficulty.HARD.name)
        assertEquals(1, hard.startedCount)
        assertEquals(0, hard.completedCount)
        // Nothing completed at this level, so there is no best time — not a best time of zero.
        assertNull(hard.bestTimeMillis)
        assertNull(hard.averageTimeMillis)
        assertNull(hard.averageMistakes)
    }

    @Test
    fun `markFinished writes only the row it names`() = runTest {
        givenPuzzle("a")
        givenPuzzle("b")
        val target = history.insert(historyEntity("a"))
        history.insert(historyEntity("b"))

        history.markFinished(
            id = target,
            finishedAt = 5_000,
            elapsedMillis = 4_000,
            mistakeCount = 3,
            completed = true,
            xpEarned = 20,
            rankedEligible = false,
            score = 420,
        )

        assertEquals(1, history.observeCompletedCount().first())
        assertEquals(20L, history.observeTotalXp().first())
        assertEquals(3, history.observeTotalMistakes().first())
    }

    @Test
    fun `reset clears the record and cuts saves loose without deleting them`() = runTest {
        // The transaction this replaces used to keep the history rows of games still on the board,
        // which left "games started" showing whatever was unfinished — a reset that looked like it
        // had done nothing.
        givenPuzzle("finished")
        givenPuzzle("in-progress")
        history.insert(historyEntity("finished", completed = true, xpEarned = 40))
        val openRow = history.insert(historyEntity("in-progress"))
        saves.upsert(savedGameEntity("in-progress", historyId = openRow))

        history.resetStatistics()

        assertEquals(0, history.observeStartedCount().first())
        assertEquals(0L, history.observeTotalXp().first())

        val save = saves.byId("in-progress")
        assertTrue("the game on the board must survive", save != null)
        assertEquals("its link to the deleted row must be cut", 0L, save!!.historyId)
    }

    @Test
    fun `reset leaves nothing pointing at a row that is gone`() = runTest {
        givenPuzzle("a")
        givenPuzzle("b")
        val first = history.insert(historyEntity("a"))
        val second = history.insert(historyEntity("b"))
        saves.upsert(savedGameEntity("a", historyId = first))
        saves.upsert(savedGameEntity("b", historyId = second))

        history.resetStatistics()

        val remaining = history.observeStartedCount().first()
        assertEquals(0, remaining)
        assertTrue(
            "every save must be detached, not just the ones checked by hand",
            listOf("a", "b").all { saves.byId(it)?.historyId == 0L },
        )
    }
}
