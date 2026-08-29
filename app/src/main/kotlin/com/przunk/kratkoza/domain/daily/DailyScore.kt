package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The score for a finished Daily (project.md section 29).
 *
 *     score = (solving(level) + speed bonus) × 0.7 ^ mistakes
 *     speed bonus = solving(level) × 2/3 × 0.5 ^ (time / target(level))
 *
 * **Solving the board is paid for, and speed is added on top.** That is the whole shape, and it
 * replaced a version where time *multiplied* the score instead of adding to it. Multiplying let the
 * clock annihilate everything: on Easy the spread between a fast solve and a very slow one was
 * forty-four fold, so finishing the board meant almost nothing and only the stopwatch counted. It
 * was reported from a device as a solved Easy board worth twelve points. The formula had not
 * misbehaved — it had faithfully counted four and a half hours.
 *
 * That number is the reason the shape had to change rather than its constants. A ranked Daily is
 * timed against the wall clock from the moment it is opened and cannot be paused (section 28), which
 * is deliberate: an in-app timer stops when the process does and would hand an hour of free thinking
 * to anyone who backgrounded the app. But it means "slow" usually records *going away*, not thinking
 * hard, and going away should not empty a solved board of its value.
 *
 * **The bonus halves every target time.** No cliff and no deadline: being faster always pays a
 * little more, at any timescale, and there is never a moment where speed stops counting. Two slow
 * players are still ordered against each other, which matters because most players are slow ones.
 *
 * **A hard board is worth more than an easy one**, before speed enters into it. An earlier version
 * scored purely on performance relative to the level, so a flawless Easy beat a good Master.
 *
 * **Guessing must lose.** The final factor is section 29 verbatim, and is the one part of this the
 * specification fixes; section 39 leaves the time-dependent part open, which is what this answers.
 */
object DailyScore {

    /** Section 29's penalty, verbatim: three mistakes leave 34% of the score. */
    private const val MISTAKE_FACTOR = 0.7

    /**
     * The speed bonus at its largest, as a share of the solving points.
     *
     * Two thirds puts a fast solve at roughly 1.6 times a very slow one. Enough to order a
     * leaderboard where everyone has the same board, without letting the clock decide it outright.
     */
    private const val MAX_BONUS_SHARE = 2.0 / 3.0

    /**
     * What solving this level is worth, whatever the clock says.
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
     * A yardstick, not a deadline — nothing expires when it passes, and nothing is taken away. It is
     * the time over which the speed bonus halves: solve within it and most of the bonus is still
     * there, take four times as long and a sixteenth of it is.
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

        val solving = baseFor(difficulty).toDouble()
        val target = targetMinutesFor(difficulty) * 60_000.0
        val bonus = solving * MAX_BONUS_SHARE * 0.5.pow(elapsedMillis / target)

        return ((solving + bonus) * MISTAKE_FACTOR.pow(mistakeCount)).roundToInt()
    }
}
