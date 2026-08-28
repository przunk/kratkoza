package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType

/**
 * What closing a game out produced, for whoever has to act on it afterwards.
 *
 * The score is carried rather than recomputed. It is decided once, when the game ends, and a second
 * evaluation somewhere else would eventually disagree with the row that was actually stored.
 */
data class FinishedGame(
    val historyId: Long,
    val gameType: GameType,
    val difficulty: Difficulty,
    /** `null` for a solo game and for an unfinished board — which is not the same as zero. */
    val score: Int?,
    /** Whether the game was played under conditions the ranking accepts (project.md section 29). */
    val rankedEligible: Boolean,
)
