package com.przunk.kratkoza.domain.ranking


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

    /**
     * Opens the leaderboard for one Daily level.
     *
     * Unlike submitting, this answers with a reason when it fails. Submitting happens by itself and
     * there is nothing for the player to do about it; opening a leaderboard happens because somebody
     * pressed a button, and a button that does nothing when pressed tells them their phone is
     * broken.
     */
    suspend fun show(board: Leaderboard): LeaderboardOutcome

    /** Sends one value to one board, answering whether it landed and why not when it did not. */
    suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome
}

/**
 * What came of trying to open a leaderboard.
 *
 * [Failed] carries the reason as text rather than a code because the only consumer is a message on
 * screen, and the reasons come from an SDK whose failures are not an enumerable set.
 */
sealed interface LeaderboardOutcome {
    /** [target] names what actually handled the intent, which is not always what one expects. */
    data class Shown(val target: String) : LeaderboardOutcome
    data class Failed(val detail: String) : LeaderboardOutcome
}

/**
 * What came of sending a score.
 *
 * A player never sees [Failed]: the board is solved and recorded either way, and there is nothing
 * for them to do about it. Debug builds show it, because "did the score actually travel" is
 * otherwise unanswerable from outside.
 */
sealed interface SubmissionOutcome {
    /**
     * Confirmed by the service: the score is on the board.
     *
     * [newBest] says whether it actually changed anything. A leaderboard keeps a player's best
     * score, so submitting a worse one succeeds and leaves the board exactly as it was — which
     * looks identical to a failure from outside, and is the opposite of one.
     */
    data class Submitted(val newBest: Boolean) : SubmissionOutcome

    /**
     * Handed over, not confirmed.
     *
     * Play Games can accept a score without being able to say what became of it, and delivering it
     * afterwards is then its own business. That is a real success from the game's side and a
     * genuinely unknown one from the record's, so it is neither of the other two.
     */
    data class Queued(val detail: String) : SubmissionOutcome

    data class Failed(val detail: String) : SubmissionOutcome
}

/** The ranking when there is none: nobody is signed in and nothing can be shown or sent. */
object NoRanking : Ranking {
    override val isAvailable: Boolean = false
    override suspend fun isSignedIn(): Boolean = false
    override suspend fun signIn(): Boolean = false
    override suspend fun show(board: Leaderboard): LeaderboardOutcome =
        LeaderboardOutcome.Failed("Play Games is not configured in this build")
    override suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome =
        SubmissionOutcome.Failed("Play Games is not configured in this build")
}
