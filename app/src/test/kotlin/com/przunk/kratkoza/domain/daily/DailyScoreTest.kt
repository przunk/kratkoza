package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScoreTest {

    private fun minutes(count: Double) = (count * 60_000).toLong()

    @Test
    fun `the target time scores half of the maximum`() {
        for (difficulty in Difficulty.entries) {
            val target = minutes(DailyScore.targetMinutesFor(difficulty).toDouble())
            assertEquals("$difficulty", 500, DailyScore.of(difficulty, target, mistakeCount = 0))
        }
    }

    @Test
    fun `an instant solve approaches the maximum without exceeding it`() {
        assertEquals(1000, DailyScore.of(Difficulty.HARD, elapsedMillis = 0, mistakeCount = 0))
    }

    @Test
    fun `twice the target scores a third`() {
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.HARD).toDouble())
        assertEquals(333, DailyScore.of(Difficulty.HARD, target * 2, mistakeCount = 0))
    }

    @Test
    fun `a slower solve always scores less, with no step anywhere`() {
        // Smoothness is the point: no single second may be worth a large jump, or the ranking turns
        // into a race against a threshold rather than against the clock.
        var previous = Int.MAX_VALUE
        var biggestDrop = 0
        for (second in 0..3600 step 5) {
            val score = DailyScore.of(Difficulty.MASTER, second * 1000L, mistakeCount = 0)
            assertTrue("score rose at ${second}s", score <= previous)
            if (previous != Int.MAX_VALUE) biggestDrop = maxOf(biggestDrop, previous - score)
            previous = score
        }
        assertTrue("a five-second step cost $biggestDrop points", biggestDrop <= 5)
    }

    @Test
    fun `the score never reaches zero however long it takes`() {
        // A slow solve is a poor result, not a void one.
        assertTrue(DailyScore.of(Difficulty.EASY, minutes(600.0), mistakeCount = 0) > 0)
    }

    @Test
    fun `mistakes cost what project md section 29 says they cost`() {
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.EASY).toDouble())
        val clean = DailyScore.of(Difficulty.EASY, target, mistakeCount = 0)

        assertEquals(500, clean)
        assertEquals(350, DailyScore.of(Difficulty.EASY, target, mistakeCount = 1))
        assertEquals(245, DailyScore.of(Difficulty.EASY, target, mistakeCount = 2))
        // 171, not the 172 that 500 × 0.7³ gives on paper: 0.7³ is a shade under 0.343 in binary
        // floating point, so the product lands just below the halfway point and rounds down. Pinned
        // rather than papered over, because a score that quietly shifts by one is worth noticing.
        assertEquals(171, DailyScore.of(Difficulty.EASY, target, mistakeCount = 3))
    }

    @Test
    fun `guessing loses to solving slowly`() {
        // The whole reason section 29 wants the penalty severe. Three mistakes must cost more than
        // taking twice as long.
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.HARD).toDouble())
        val fastButGuessing = DailyScore.of(Difficulty.HARD, target, mistakeCount = 3)
        val slowAndClean = DailyScore.of(Difficulty.HARD, target * 2, mistakeCount = 0)

        assertTrue("$fastButGuessing should lose to $slowAndClean", fastButGuessing < slowAndClean)
    }

    @Test
    fun `the same relative performance scores the same on every level`() {
        // What the per-level target buys: solving a Master board in Master time is worth as much as
        // solving an Easy board in Easy time, so the three boards of a day are comparable.
        val scores = Difficulty.entries.map {
            DailyScore.of(it, minutes(DailyScore.targetMinutesFor(it).toDouble()), mistakeCount = 0)
        }
        assertEquals(1, scores.toSet().size)
    }

    @Test
    fun `harder levels are given more time`() {
        val targets = Difficulty.entries.map(DailyScore::targetMinutesFor)
        assertEquals(targets.sorted(), targets)
        assertTrue(targets.all { it > 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative time is refused`() {
        DailyScore.of(Difficulty.EASY, elapsedMillis = -1, mistakeCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative mistake count is refused`() {
        DailyScore.of(Difficulty.EASY, elapsedMillis = 0, mistakeCount = -1)
    }
}
