package com.przunk.kratkoza.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.model.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What happens to a day that was already generated when the Daily recipe changes underneath it.
 *
 * `RULESET_VERSION` exists so the recipe *can* change, and a phone that has already opened today
 * under the old one is the normal case on the day of an update, not an edge case. So the day's
 * identity has to carry the ruleset it was made for; two rows for the same date and difficulty made
 * under different recipes are different rows, and the newer one has to be able to land beside the
 * older one.
 */
@RunWith(AndroidJUnit4::class)
class DailyPuzzleDaoTest {

    private val database = createTestDatabase()
    private val dao = database.dailyPuzzleDao()
    private val puzzles = database.puzzleDao()
    private val date = "2026-08-29"

    @After
    fun tearDown() = database.close()

    private suspend fun store(difficulty: Difficulty, rulesetVersion: Int) {
        val id = "puzzle-${difficulty.name}-v$rulesetVersion"
        puzzles.insert(puzzleEntity(id, difficulty = difficulty))
        dao.insert(
            listOf(
                DailyPuzzleEntity(
                    date = date,
                    difficulty = difficulty.name,
                    puzzleId = id,
                    rulesetVersion = rulesetVersion,
                ),
            ),
        )
    }

    @Test
    fun `a new ruleset generates a full day over one already stored under the old ruleset`() =
        runTest {
            // Ruleset 1 shipped Easy, Hard and Master, and this phone opened the day under it.
            store(Difficulty.EASY, rulesetVersion = 1)
            store(Difficulty.HARD, rulesetVersion = 1)
            store(Difficulty.MASTER, rulesetVersion = 1)

            // Ruleset 2 keeps Easy and Hard by name and replaces Master with Expert.
            store(Difficulty.EASY, rulesetVersion = 2)
            store(Difficulty.HARD, rulesetVersion = 2)
            store(Difficulty.EXPERT, rulesetVersion = 2)

            val today = dao.forDate(date, rulesetVersion = 2)

            assertEquals(
                "the new ruleset's whole day has to be readable, not only the board whose " +
                    "difficulty the old ruleset happened not to use",
                setOf(Difficulty.EASY.name, Difficulty.HARD.name, Difficulty.EXPERT.name),
                today.map { it.difficulty }.toSet(),
            )
        }

    @Test
    fun `the old ruleset's day is left as it was`() = runTest {
        store(Difficulty.EASY, rulesetVersion = 1)
        store(Difficulty.EASY, rulesetVersion = 2)

        val old = dao.forDate(date, rulesetVersion = 1).single()

        assertEquals("puzzle-EASY-v1", old.puzzleId)
    }

    @Test
    fun `a saved board keeps its cycle date after the ruleset changes`() = runTest {
        store(Difficulty.EASY, rulesetVersion = 1)

        assertEquals(date, dao.dateOfPuzzle("puzzle-EASY-v1"))
    }
}
