package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The score for a finished Daily (project.md section 29).
 *
 *     score = 2 × base(level) × target(level) / (target + time) × 0.7 ^ mistakes
 *
 * Three things it has to do at once, and each part of the formula does one of them.
 *
 * **A hard board must be worth more than an easy one.** That is what [baseFor] is: the points a
 * solid solve of that level is worth, before speed enters into it. An earlier version scored purely
 * on performance relative to the level, so a flawless Easy beat a good Master — the level did the
 * work and then vanished from the answer.
 *
 * **Speed must pay, without a deadline.** The time factor is exactly 1 at the target, so the target
 * is not a limit but a yardstick: hit it and the base is what you score. Beat it and the factor
 * climbs towards 2; miss it and the factor falls away smoothly, forever, never reaching zero.
 * A slow solve is a poor score, not a void one, and two slow players are still ordered against each
 * other — which matters, because most players are slow ones.
 *
 * **Guessing must lose.** The last factor is project.md section 29 verbatim.
 *
 * project.md section 39 leaves the exact shape open; this is the agreed working answer.
 */
object DailyScore {

    /** Section 29's penalty, verbatim: three mistakes leave 34% of the score. */
    private const val MISTAKE_FACTOR = 0.7

    /** The most speed alone can be worth. Instant solve doubles the base and no more. */
    private const val MAX_TIME_MULTIPLIER = 2.0

    /**
     * What a solid solve of this level is worth, before speed.
     *
     * The spread is deliberate and roughly fourfold from end to end: a Master board asks for
     * techniques an Easy board never needs, and the score should say so without the player having
     * to be fast as well.
     */
    fun baseFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 500
        Difficulty.NORMAL -> 700
        Difficulty.HARD -> 1000
        Difficulty.EXPERT -> 1400
        Difficulty.MASTER -> 2000
    }

    /**
     * The time a solve is measured against, in minutes.
     *
     * A yardstick, not a deadline — nothing expires when it passes. Hitting it exactly scores
     * [baseFor]; that is the whole meaning of the number.
     *
     * Chosen by judgement. An attempt to derive them from the solver failed and is worth recording:
     * counting the steps a logical solve takes gives 45 / 58 / 63 across the three Daily levels, a
     * spread of barely half, while the human spread is several times. What makes a hard board hard
     * is *finding* the technique, not applying it, and no count of applications sees that.
     */
    fun targetMinutesFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.NORMAL -> 9
        Difficulty.HARD -> 15
        Difficulty.EXPERT -> 20
        Difficulty.MASTER -> 25
    }

    /**
     * The score for solving [difficulty] in [elapsedMillis] with [mistakeCount] mistakes.
     *
     * Only ever called for a game that was actually finished. An unfinished board has no score,
     * which is not the same as a score of zero.
     */
    fun of(difficulty: Difficulty, elapsedMillis: Long, mistakeCount: Int): Int {
        require(elapsedMillis >= 0) { "Time cannot be negative, got $elapsedMillis" }
        require(mistakeCount >= 0) { "Mistakes cannot be negative, got $mistakeCount" }

        val target = targetMinutesFor(difficulty) * 60_000.0
        val timeFactor = MAX_TIME_MULTIPLIER * target / (target + elapsedMillis)
        return (baseFor(difficulty) * timeFactor * MISTAKE_FACTOR.pow(mistakeCount)).roundToInt()
    }
}
