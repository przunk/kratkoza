package com.przunk.kratkoza.domain.ranking

import com.przunk.kratkoza.domain.model.Difficulty

/**
 * The public ranking a Daily result travels to (project.md section 27).
 *
 * An interface rather than a direct call to Play Games, because everything above this line has to
 * stay runnable on the JVM: the SDK insists on an `Activity` for every one of its clients, and a
 * ViewModel that reached for one could not be tested at all.
 *
 * Every method answers with a plain boolean rather than throwing. There is no failure here the
 * player can act on — no configuration, no signed-in account, no network all look the same from
 * the game's side, and all of them mean the same thing: the board still counts locally.
 */
interface Ranking {

    /** Whether a ranking exists to talk to — configured, with an SDK behind it. */
    val isAvailable: Boolean

    /** Whether somebody is signed in right now. */
    suspend fun isSignedIn(): Boolean

    /**
     * Asks the player to sign in, which may show an account chooser.
     *
     * Only ever called because the player pressed something. Play Games attempts a silent sign-in
     * of its own when the process starts, and that is the only automatic one there should be.
     */
    suspend fun signIn(): Boolean

    /** Opens the leaderboard for one Daily level, answering whether it could be shown. */
    suspend fun showLeaderboard(difficulty: Difficulty): Boolean

    /** Sends one Daily score, answering whether it landed. */
    suspend fun submitDailyScore(difficulty: Difficulty, score: Int): Boolean
}

/** The ranking when there is none: nobody is signed in and nothing can be shown or sent. */
object NoRanking : Ranking {
    override val isAvailable: Boolean = false
    override suspend fun isSignedIn(): Boolean = false
    override suspend fun signIn(): Boolean = false
    override suspend fun showLeaderboard(difficulty: Difficulty): Boolean = false
    override suspend fun submitDailyScore(difficulty: Difficulty, score: Int): Boolean = false
}
