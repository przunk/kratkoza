package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The score for a finished Daily (project.md section 29).
 *
 * Two parts, and section 29 fixes the second of them:
 *
 *     score = 1000 × target / (target + time)      then      × 0.7 ^ mistakes
 *
 * The first half decays smoothly instead of stepping: an instant solve approaches 1000, the target
 * time scores 500, twice the target 333. Nothing falls off a cliff, so no single second is ever
 * worth a hundred points, and nothing reaches zero however long the board is left open — a slow
 * solve is a poor score, not a void one.
 *
 * The second half is section 29 verbatim, and it is severe on purpose: three mistakes leave 34% of
 * the score. Guessing has to be a losing strategy or the ranking measures patience rather than
 * skill.
 *
 * project.md section 39 leaves the exact formula open, so this is the agreed working answer.
 */
object DailyScore {

    private const val MAX_BASE = 1000.0

    /** Section 29's penalty, verbatim. */
    private const val MISTAKE_FACTOR = 0.7

    /**
     * The time a level is scaled against, in minutes.
     *
     * These are a judgement call, and deliberately so — an attempt to derive them from the solver
     * failed and is worth recording: counting the steps a logical solve takes gives 45 / 58 / 63
     * across the three levels, a spread of barely half, while the human spread is several times.
     * What makes a hard board hard is *finding* the technique, not applying it, and no count of
     * applications sees that.
     *
     * Being wrong here costs little. Everybody on one board shares its target, so within a level the
     * ordering is identical whatever the number is. It matters only when comparing levels or adding
     * up a day, which is exactly where it can be tuned once there are real times to look at.
     */
    fun targetMinutesFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 6
        Difficulty.NORMAL -> 10
        Difficulty.HARD -> 18
        Difficulty.EXPERT -> 28
        Difficulty.MASTER -> 40
    }

    /**
     * The score for solving [difficulty] in [elapsedMillis] with [mistakeCount] mistakes.
     *
     * Only ever called for a game that was actually finished; an unfinished board has no score,
     * which is not the same as a score of zero.
     */
    fun of(difficulty: Difficulty, elapsedMillis: Long, mistakeCount: Int): Int {
        require(elapsedMillis >= 0) { "Time cannot be negative, got $elapsedMillis" }
        require(mistakeCount >= 0) { "Mistakes cannot be negative, got $mistakeCount" }

        val target = targetMinutesFor(difficulty) * 60_000.0
        val base = MAX_BASE * target / (target + elapsedMillis)
        return (base * MISTAKE_FACTOR.pow(mistakeCount)).roundToInt()
    }
}
