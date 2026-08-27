package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScoreTest {

    private fun minutes(count: Double) = (count * 60_000).toLong()

    @Test
    fun `hitting the target scores exactly the level's base`() {
        // The meaning of the target: a yardstick, not a deadline. Hit it and you score what the
        // level is worth.
        for (difficulty in Difficulty.entries) {
            val target = minutes(DailyScore.targetMinutesFor(difficulty).toDouble())
            assertEquals(
                "$difficulty",
                DailyScore.baseFor(difficulty),
                DailyScore.of(difficulty, target, mistakeCount = 0),
            )
        }
    }

    @Test
    fun `a harder board is worth more than an easier one played equally well`() {
        // The thing the previous formula got wrong: it scored performance relative to the level, so
        // the level itself disappeared from the answer and a flawless Easy beat a good Master.
        val atTarget = Difficulty.entries.map {
            DailyScore.of(it, minutes(DailyScore.targetMinutesFor(it).toDouble()), mistakeCount = 0)
        }
        assertEquals(atTarget.sorted(), atTarget)
        assertTrue(
            "the spread should be several-fold, not marginal",
            atTarget.last() >= atTarget.first() * 3,
        )
    }

    @Test
    fun `speed is worth at most double, never more`() {
        // Bounded on purpose: an unbounded bonus would make a suspiciously fast time worth any
        // number of ordinary ones.
        for (difficulty in Difficulty.entries) {
            assertEquals(
                "$difficulty",
                DailyScore.baseFor(difficulty) * 2,
                DailyScore.of(difficulty, elapsedMillis = 0, mistakeCount = 0),
            )
        }
    }

    @Test
    fun `twice the target scores two thirds of the base`() {
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.HARD).toDouble())
        assertEquals(667, DailyScore.of(Difficulty.HARD, target * 2, mistakeCount = 0))
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
        assertTrue("a five-second step cost $biggestDrop points", biggestDrop <= 20)
    }

    @Test
    fun `the score never reaches zero however long it takes`() {
        // A slow solve is a poor result, not a void one.
        assertTrue(DailyScore.of(Difficulty.EASY, minutes(600.0), mistakeCount = 0) > 0)
    }

    @Test
    fun `mistakes cost what project md section 29 says they cost`() {
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.EASY).toDouble())

        assertEquals(500, DailyScore.of(Difficulty.EASY, target, mistakeCount = 0))
        assertEquals(350, DailyScore.of(Difficulty.EASY, target, mistakeCount = 1))
        assertEquals(245, DailyScore.of(Difficulty.EASY, target, mistakeCount = 2))
        // 171, not the 172 that 500 × 0.7³ gives on paper: 0.7³ is a shade under 0.343 in binary
        // floating point, so the product lands just below the halfway point and rounds down. Pinned
        // rather than papered over, because a score that quietly shifts by one is worth noticing.
        assertEquals(171, DailyScore.of(Difficulty.EASY, target, mistakeCount = 3))
    }

    @Test
    fun `a mistake on a hard board costs more than a whole easy board is worth`() {
        // What the level weighting buys, and the answer to "mistakes do not hurt enough": one
        // mistake on Master gives up more points than a flawless Easy earns.
        val masterTarget = minutes(DailyScore.targetMinutesFor(Difficulty.MASTER).toDouble())
        val cost = DailyScore.of(Difficulty.MASTER, masterTarget, 0) -
            DailyScore.of(Difficulty.MASTER, masterTarget, 1)

        assertTrue(cost > DailyScore.baseFor(Difficulty.EASY))
    }

    @Test
    fun `no number of mistakes empties the score`() {
        // project.md section 16: there are no lives, and a mistake does not end the game. A score
        // that reached zero would end it in every way that matters, leaving nothing to play for.
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.MASTER).toDouble())
        assertTrue(DailyScore.of(Difficulty.MASTER, target, mistakeCount = 8) > 0)
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
    fun `harder levels are worth more before speed is considered`() {
        val bases = Difficulty.entries.map(DailyScore::baseFor)
        assertEquals(bases.sorted(), bases)
        assertEquals("no two levels may be worth the same", bases.size, bases.toSet().size)
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
