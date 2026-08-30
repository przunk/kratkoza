package com.przunk.kratkoza.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.model.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The archive projections against Room: joins, ordering, counts and reset independence. */
@RunWith(AndroidJUnit4::class)
class SolvedBoardDaoTest {

    private lateinit var database: KratkozaDatabase
    private lateinit var archive: SolvedBoardDao

    @Before
    fun setUp() {
        database = createTestDatabase()
        archive = database.solvedBoardDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `entries are newest first and count every completion of the same board`() = runTest {
        database.puzzleDao().insert(puzzleEntity("repeated", Difficulty.HARD))
        val olderId = archive.insert(solvedBoardEntity("repeated", completedAt = 1_000))
        val newerId = archive.insert(solvedBoardEntity("repeated", completedAt = 9_000))

        val rows = archive.observeAll().first()

        assertEquals(listOf(newerId, olderId), rows.map { it.id })
        assertTrue(rows.all { it.completionCount == 2 })
        assertEquals(Difficulty.HARD.name, rows.first().difficulty)
        assertEquals(".".repeat(81), rows.first().givens)
    }

    @Test
    fun `the detail reports whether the archived board is already in progress`() = runTest {
        database.puzzleDao().insert(puzzleEntity("again"))
        val id = archive.insert(solvedBoardEntity("again", completedAt = 1_000))
        assertFalse(archive.observeById(id).first()!!.inProgress)

        database.savedGameDao().upsert(savedGameEntity("again", historyId = 7))

        assertTrue(archive.observeById(id).first()!!.inProgress)
    }

    @Test
    fun `resetting statistics leaves the archive untouched`() = runTest {
        database.puzzleDao().insert(puzzleEntity("kept"))
        archive.insert(solvedBoardEntity("kept", completedAt = 2_000))
        database.gameHistoryDao().insert(historyEntity("kept", completed = true))

        database.gameHistoryDao().resetStatistics()

        assertEquals(1, archive.observeAll().first().size)
        assertEquals(0, database.gameHistoryDao().observeStartedCount().first())
    }
}

private fun solvedBoardEntity(puzzleId: String, completedAt: Long) = SolvedBoardEntity(
    puzzleId = puzzleId,
    completedAt = completedAt,
    elapsedMillis = 60_000,
    mistakeCount = 1,
    xpEarned = 40,
    score = null,
)
