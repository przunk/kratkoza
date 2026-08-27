package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import java.time.LocalDate

/** One of the three boards of a day, and how far the player has got with it. */
data class DailyEntry(
    val date: LocalDate,
    val difficulty: Difficulty,
    val puzzleId: String,
    val status: DailyStatus,
    /** Time played so far, or the finishing time once it is done. */
    val elapsedMillis: Long?,
    val mistakeCount: Int?,
    /** project.md section 29. Only a finished board has one; `null` is "not scored", not zero. */
    val score: Int?,
    /**
     * Whether finishing this board would still count for a public ranking (project.md section 28).
     *
     * Nothing is ranked yet, so nothing reads this to decide anything — but the rule that loses
     * eligibility is enforced from the start, because retrofitting it later would mean asking games
     * already in progress a question their history cannot answer.
     */
    val rankedEligible: Boolean,
)

enum class DailyStatus {
    NOT_STARTED,

    /** Started and still on the board. */
    IN_PROGRESS,

    /** Correctly solved (project.md section 16). */
    COMPLETED,
}
