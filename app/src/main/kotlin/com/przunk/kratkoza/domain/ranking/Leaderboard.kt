package com.przunk.kratkoza.domain.ranking

import com.przunk.kratkoza.domain.daily.DailyTotals
import com.przunk.kratkoza.domain.model.Difficulty

/**
 * The public boards a result can travel to (project.md section 27).
 *
 * This enum is the single declaration of a board. Adding one means adding a constant here and
 * answering the two `when` expressions below; the compiler then also demands its identifier in
 * `PlayGamesConfig`. Nothing else has a list of boards to fall out of step with — which is the
 * point, because the previous arrangement kept "which boards exist" in three places and only one
 * of them failed to compile when they drifted.
 *
 * Both totals work because Play Games keeps a player's *best* submission and a total only ever
 * grows: the highest number it has seen is therefore the current total, not a peak since passed.
 */
enum class Leaderboard {
    DAILY_EASY,
    DAILY_HARD,
    DAILY_EXPERT,

    /**
     * Today's three boards added together.
     *
     * Read through Play Games' own daily span, which resets on its schedule — so the board shows
     * each player's finished day and starts empty on the next one, without the app resetting
     * anything itself.
     */
    DAILY_TOTAL,

    /** Every Daily ever finished, added together. */
    ALL_TIME_TOTAL,

    ;

    /**
     * The stretch of time the board is about.
     *
     * Play Games keeps every board under three spans at once and its overlay opens on whichever the
     * viewer last chose — so a daily board could open on all-time and show nothing from today. The
     * app states the span instead of leaving it to a remembered preference.
     */
    val span: Span
        get() = when (this) {
            DAILY_EASY, DAILY_HARD, DAILY_EXPERT, DAILY_TOTAL -> Span.TODAY
            ALL_TIME_TOTAL -> Span.ALL_TIME
        }

    /**
     * Whether this board carries one game's own result rather than a running sum.
     *
     * `publiclyRanked` follows these and not the totals: it records that *this game* reached the
     * ranking, and a sum accepted on its behalf would not be the same claim.
     */
    val carriesOwnResult: Boolean
        get() = when (this) {
            DAILY_EASY, DAILY_HARD, DAILY_EXPERT -> true
            DAILY_TOTAL, ALL_TIME_TOTAL -> false
        }

    /**
     * What this board should be told after one finished Daily, or `null` when it has nothing to
     * hear — a level board only listens to its own level.
     */
    fun valueFor(result: DailyResult): Long? = when (this) {
        // Compared through forDaily rather than matched again, so the difficulty-to-board mapping
        // exists once and cannot disagree with itself.
        DAILY_EASY, DAILY_HARD, DAILY_EXPERT ->
            result.score.toLong().takeIf { forDaily(result.difficulty) == this }

        DAILY_TOTAL -> result.totals?.today
        ALL_TIME_TOTAL -> result.totals?.allTime
    }

    /** How far back a board looks. Named here rather than in Play Games' own terms. */
    enum class Span { TODAY, ALL_TIME }

    companion object {
        /** The board for one Daily level, or `null` for a level Daily does not run. */
        fun forDaily(difficulty: Difficulty): Leaderboard? = when (difficulty) {
            Difficulty.EASY -> DAILY_EASY
            Difficulty.HARD -> DAILY_HARD
            Difficulty.EXPERT -> DAILY_EXPERT
            Difficulty.NORMAL, Difficulty.MASTER -> null
        }
    }
}

/**
 * One finished Daily, in the terms the boards care about.
 *
 * Exists so that [Leaderboard.valueFor] can be a plain function of the result rather than each
 * board's value being fetched separately at the call site — which is how the submitter came to hold
 * its own list of what to send where.
 */
data class DailyResult(
    val difficulty: Difficulty,
    val score: Int,
    /**
     * `null` when the day's totals could not be read.
     *
     * The level board still has everything it needs — this game's own score — so one unreadable
     * sum must not cost the submission that does not depend on it.
     */
    val totals: DailyTotals?,
)
