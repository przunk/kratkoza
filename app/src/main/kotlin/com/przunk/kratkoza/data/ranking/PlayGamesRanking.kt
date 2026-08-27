package com.przunk.kratkoza.data.ranking

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.Ranking
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The Play Games implementation of [Ranking].
 *
 * Sign-in is never initiated from here. The SDK attempts it by itself when the process starts, and
 * a game that has just been solved is the worst possible moment to throw an account chooser over
 * the board. If the player is not signed in, the score simply does not travel.
 */
class PlayGamesRanking(
    private val config: PlayGamesConfig,
    private val activities: ActivityHolder,
) : Ranking {

    override val isAvailable: Boolean
        get() = config.isConfigured

    override suspend fun isSignedIn(): Boolean {
        val activity = activities.current() ?: return false
        return signedIn(activity)
    }

    override suspend fun signIn(): Boolean {
        val activity = activities.current() ?: return false
        return PlayGames.getGamesSignInClient(activity).signIn().awaitOrNull()?.isAuthenticated == true
    }

    override suspend fun showLeaderboard(difficulty: Difficulty): LeaderboardOutcome {
        val leaderboard = config.leaderboardFor(difficulty)
            ?: return LeaderboardOutcome.Failed("no leaderboard id for $difficulty")
        val activity = activities.current()
            ?: return LeaderboardOutcome.Failed("no activity attached")

        val request = PlayGames.getLeaderboardsClient(activity).getLeaderboardIntent(leaderboard)
        val intent = request.awaitOrNull()
            ?: return LeaderboardOutcome.Failed(describe(request.exception, leaderboard))

        // Play Games resolves this against its own overlay, which is not guaranteed to be present.
        return runCatching { activity.startActivity(intent) }
            .fold(
                onSuccess = { LeaderboardOutcome.Shown },
                onFailure = { LeaderboardOutcome.Failed("cannot open Play Games: ${it.message}") },
            )
    }

    /**
     * The failure in a form somebody can act on.
     *
     * The leaderboard id is included because the three differ only in their last character, and
     * "which board did it actually ask for" is the first question worth answering.
     */
    private fun describe(cause: Exception?, leaderboard: String): String {
        val reason = cause?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "no reason given"
        return "$leaderboard — $reason"
    }

    override suspend fun submitDailyScore(difficulty: Difficulty, score: Int): Boolean {
        val leaderboard = config.leaderboardFor(difficulty) ?: return false
        val activity = activities.current() ?: return false
        if (!signedIn(activity)) return false

        // submitScoreImmediate rather than submitScore: the fire-and-forget variant reports nothing,
        // and a caller that records "this reached the ranking" would be recording a guess.
        return PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(leaderboard, score.toLong())
            .awaitOrNull() != null
    }

    private suspend fun signedIn(activity: Activity): Boolean =
        PlayGames.getGamesSignInClient(activity).isAuthenticated().awaitOrNull()?.isAuthenticated == true

    /**
     * A Play Services [Task] as a suspending call, treating any failure as absence.
     *
     * Written out rather than pulled in with kotlinx-coroutines-play-services: that dependency
     * exists to provide this, and technical_requirements.md section 8 asks for restraint.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
}

/**
 * Holds the Activity the Play Games SDK demands, weakly.
 *
 * Every Play Games client is obtained from an `Activity`, but the object that wants one lives in
 * the application graph and outlives any single screen. A weak reference is what keeps that
 * mismatch from turning into a leaked Activity on every rotation.
 */
class ActivityHolder {

    private var reference: WeakReference<Activity> = WeakReference(null)

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    fun detach() {
        reference.clear()
    }

    fun current(): Activity? = reference.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}
