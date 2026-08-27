package com.przunk.kratkoza.domain.ranking

import com.przunk.kratkoza.domain.model.Difficulty

/**
 * Sending a Daily result to the public ranking (project.md section 27).
 *
 * An interface rather than a direct call to Play Games, because everything above this line has to
 * stay runnable on the JVM: the SDK insists on an `Activity` for every one of its clients, and a
 * ViewModel that reached for one could not be tested at all.
 */
interface Ranking {

    /** Whether submitting is worth attempting at all — configured, with an SDK behind it. */
    val isAvailable: Boolean

    /**
     * Sends one Daily score, answering whether it landed.
     *
     * `false` covers every ordinary reason a score does not reach the ranking: no configuration, no
     * signed-in player, no network. None of them is an error the player needs to hear about — the
     * game is finished and recorded either way, and the ranking is the part that can wait.
     */
    suspend fun submitDailyScore(difficulty: Difficulty, score: Int): Boolean
}

/** The ranking when there is none: every submission politely fails. */
object NoRanking : Ranking {
    override val isAvailable: Boolean = false
    override suspend fun submitDailyScore(difficulty: Difficulty, score: Int): Boolean = false
}
