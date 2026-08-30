package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScoreTest {

    private fun minutes(count: Double) = (count * 60_000).toLong()

    @Test
    fun `solving the board is paid for however long it takes`() {
        // The property the whole shape exists for. Time adds to the score and cannot eat it, so a
        // board solved over a whole day is still worth what solving it is worth. Under the previous
        // formula the same solve came back as twelve points.
        for (difficulty in Difficulty.entries) {
            assertEquals(
                "$difficulty after a day",
                DailyScore.baseFor(difficulty),
                DailyScore.of(difficulty, minutes(1440.0), mistakeCount = 0),
            )
        }
    }

    @Test
    fun `the bonus halves over each target time`() {
        // What the target means now: not a deadline and not a par score, but the time over which
        // the speed bonus halves.
        val target = minutes(DailyScore.targetMinutesFor(Difficulty.HARD).toDouble())
        val solving = DailyScore.baseFor(Difficulty.HARD)

        val instant = DailyScore.of(Difficulty.HARD, 0, mistakeCount = 0) - solving
        val onTarget = DailyScore.of(Difficulty.HARD, target, mistakeCount = 0) - solving
        val twice = DailyScore.of(Difficulty.HARD, target * 2, mistakeCount = 0) - solving

        assertEquals(instant / 2.0, onTarget.toDouble(), 1.0)
        assertEquals(onTarget / 2.0, twice.toDouble(), 1.0)
    }

    @Test
    fun `a harder board is worth more than an easier one played equally well`() {
        // What an even earlier formula got wrong: it scored performance relative to the level, so
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
    fun `speed adds two thirds at most, never more`() {
        // Bounded on purpose: an unbounded bonus would make a suspiciously fast time worth any
        // number of ordinary ones. The spread between the fastest and the slowest solve of a level
        // is therefore about five to three.
        for (difficulty in Difficulty.entries) {
            val solving = DailyScore.baseFor(difficulty)
            assertEquals(
                "$difficulty",
                (solving * 5.0 / 3.0).roundToInt(),
                DailyScore.of(difficulty, elapsedMillis = 0, mistakeCount = 0),
            )
        }
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

        // Easy at its target time is 667: 500 for solving plus half of the 333-point bonus.
        assertEquals(667, DailyScore.of(Difficulty.EASY, target, mistakeCount = 0))
        assertEquals(467, DailyScore.of(Difficulty.EASY, target, mistakeCount = 1))
        assertEquals(327, DailyScore.of(Difficulty.EASY, target, mistakeCount = 2))
        assertEquals(229, DailyScore.of(Difficulty.EASY, target, mistakeCount = 3))
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
