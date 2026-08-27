package com.przunk.kratkoza.data.ranking

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import com.przunk.kratkoza.domain.model.Difficulty
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
        return PlayGames.getGamesSignInClient(activity).signIn().orNull()?.isAuthenticated == true
    }

    override suspend fun showLeaderboard(difficulty: Difficulty): Boolean {
        val leaderboard = config.leaderboardFor(difficulty) ?: return false
        val activity = activities.current() ?: return false
        val intent = PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(leaderboard)
            .orNull() ?: return false

        activity.startActivity(intent)
        return true
    }

    override suspend fun submitDailyScore(difficulty: Difficulty, score: Int): Boolean {
        val leaderboard = config.leaderboardFor(difficulty) ?: return false
        val activity = activities.current() ?: return false
        if (!signedIn(activity)) return false

        // submitScoreImmediate rather than submitScore: the fire-and-forget variant reports nothing,
        // and a caller that records "this reached the ranking" would be recording a guess.
        return PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(leaderboard, score.toLong())
            .orNull() != null
    }

    private suspend fun signedIn(activity: Activity): Boolean =
        PlayGames.getGamesSignInClient(activity).isAuthenticated().orNull()?.isAuthenticated == true

    /**
     * A Play Services [Task] as a suspending call, treating any failure as absence.
     *
     * Written out rather than pulled in with kotlinx-coroutines-play-services: that dependency
     * exists to provide this, and technical_requirements.md section 8 asks for restraint.
     */
    private suspend fun <T> Task<T>.orNull(): T? = suspendCancellableCoroutine { continuation ->
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
