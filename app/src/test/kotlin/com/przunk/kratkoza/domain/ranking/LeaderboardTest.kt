package com.przunk.kratkoza.domain.ranking

import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.daily.DailyTotals
import com.przunk.kratkoza.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderboardTest {

    private val totals = DailyTotals(today = 2_405, allTime = 9_001)

    private fun resultFor(difficulty: Difficulty) =
        DailyResult(difficulty = difficulty, score = 407, totals = totals)

    @Test
    fun `a level board hears only its own level`() {
        val result = resultFor(Difficulty.EASY)

        assertEquals(407L, Leaderboard.DAILY_EASY.valueFor(result))
        assertNull(Leaderboard.DAILY_HARD.valueFor(result))
        assertNull(Leaderboard.DAILY_MASTER.valueFor(result))
    }

    @Test
    fun `both totals hear every finished daily`() {
        for (difficulty in DailyPuzzles.DIFFICULTIES) {
            val result = resultFor(difficulty)
            assertEquals(totals.today, Leaderboard.DAILY_TOTAL.valueFor(result))
            assertEquals(totals.allTime, Leaderboard.ALL_TIME_TOTAL.valueFor(result))
        }
    }

    @Test
    fun `every level Daily runs feeds exactly three boards`() {
        // The invariant the submitter relies on: it walks the whole enum and sends whatever each
        // board asks for, so "which boards does one game feed" has to be answerable from here alone.
        for (difficulty in DailyPuzzles.DIFFICULTIES) {
            val result = resultFor(difficulty)
            val fed = Leaderboard.entries.filter { it.valueFor(result) != null }

            assertEquals(
                "one level board and the two totals, for $difficulty",
                listOf(
                    requireNotNull(Leaderboard.forDaily(difficulty)),
                    Leaderboard.DAILY_TOTAL,
                    Leaderboard.ALL_TIME_TOTAL,
                ).sortedBy { it.ordinal },
                fed,
            )
        }
    }

    @Test
    fun `only the level boards carry a game's own result`() {
        // publiclyRanked follows these. A total accepted on a game's behalf is not the same claim,
        // and conflating the two is the bug that made the column necessary in the first place.
        val carrying = Leaderboard.entries.filter { it.carriesOwnResult }

        assertEquals(
            DailyPuzzles.DIFFICULTIES.mapNotNull(Leaderboard::forDaily).sortedBy { it.ordinal },
            carrying,
        )
    }

    @Test
    fun `a level Daily never runs has no board`() {
        // NORMAL and EXPERT are solo-only. Silently sending their scores to a Daily board would be
        // worse than sending them nowhere.
        for (difficulty in Difficulty.entries - DailyPuzzles.DIFFICULTIES.toSet()) {
            assertNull("$difficulty is not a Daily level", Leaderboard.forDaily(difficulty))
        }
    }
}
