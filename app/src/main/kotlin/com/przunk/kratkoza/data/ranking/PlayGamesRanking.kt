package com.przunk.kratkoza.data.ranking

import android.app.Activity
import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.SubmissionOutcome
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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

        // startActivityForResult, not startActivity: the Play Games overlay is documented to be
        // launched for a result, and some of its screens close immediately when started without
        // one — which looks exactly like nothing happening.
        return runCatching { activity.startActivityForResult(intent, REQUEST_LEADERBOARD) }
            .fold(
                onSuccess = { LeaderboardOutcome.Shown(resolve(activity, intent)) },
                onFailure = { LeaderboardOutcome.Failed("cannot open Play Games: ${it.message}") },
            )
    }

    /**
     * What will actually handle the intent.
     *
     * Worth naming, because "the intent started and nothing appeared" has two very different
     * causes: something opened and closed again, or nothing on the device claims this intent at
     * all. From inside `startActivity` those are indistinguishable.
     */
    private fun resolve(activity: Activity, intent: Intent): String =
        activity.packageManager.resolveActivity(intent, 0)?.activityInfo
            ?.let { "${it.packageName}/${it.name.substringAfterLast('.')}" }
            ?: "nothing on this device claims that intent"

    /**
     * The failure in a form somebody can act on.
     *
     * The leaderboard id is included because the three differ only in their last character, and
     * "which board did it actually ask for" is the first question worth answering.
     */
    private fun describe(cause: Exception?, leaderboard: String): String {
        // Play Games codes are five digits and mean nothing on their own; the SDK can name them,
        // and a name is what makes the difference between a bug report and a number.
        val reason = when (val code = statusCodeOf(cause)) {
            null -> cause?.let { "${it::class.java.simpleName}: ${it.message}" }
                ?: "no exception — timed out after ${TASK_TIMEOUT_MILLIS}ms"

            else -> "$code ${GamesClientStatusCodes.getStatusCodeString(code)}"
        }
        return "$leaderboard — $reason"
    }

    override suspend fun submitDailyScore(difficulty: Difficulty, score: Int): SubmissionOutcome {
        val leaderboard = config.leaderboardFor(difficulty)
            ?: return SubmissionOutcome.Failed("no leaderboard id for $difficulty")
        val activity = activities.current()
            ?: return SubmissionOutcome.Failed("no activity attached")
        if (!signedIn(activity)) return SubmissionOutcome.Failed("not signed in to Play Games")

        var request = submitOnce(activity, leaderboard, score)

        // CLIENT_RECONNECT_REQUIRED means the games client's session went stale between
        // authenticating and submitting — isAuthenticated answers yes while the connection behind
        // it no longer holds. Re-authenticating and trying once more is the first remedy.
        if (statusCodeOf(request.exception) == CLIENT_RECONNECT_REQUIRED) {
            PlayGames.getGamesSignInClient(activity).signIn().awaitOrNull()
            request = submitOnce(activity, leaderboard, score)
        }

        if (request.isSuccessful) return SubmissionOutcome.Submitted

        // Nothing left to confirm with, so stop trying to confirm. submitScore returns void because
        // it does not report back at all: it queues the score inside Play Services, which delivers
        // it when the client is next healthy. That is exactly the situation here, and it is the
        // difference between a score that eventually lands and one that is thrown away for the sake
        // of a receipt.
        val reason = describe(request.exception, leaderboard)
        return runCatching {
            PlayGames.getLeaderboardsClient(activity).submitScore(leaderboard, score.toLong())
        }.fold(
            onSuccess = { SubmissionOutcome.Queued(reason) },
            onFailure = { SubmissionOutcome.Failed("$reason; queueing also failed: ${it.message}") },
        )
    }

    /**
     * One attempt, returning the task itself rather than its value.
     *
     * The caller needs the failure as well as the result: whether to retry depends on which status
     * came back, and a plain null cannot say.
     *
     * submitScoreImmediate rather than submitScore, because the fire-and-forget variant reports
     * nothing at all and a caller recording "this reached the ranking" would be recording a guess.
     */
    private suspend fun submitOnce(activity: Activity, leaderboard: String, score: Int): Task<*> {
        val request = PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(leaderboard, score.toLong())
        request.awaitOrNull()
        return request
    }

    private fun statusCodeOf(cause: Exception?): Int? = (cause as? ApiException)?.statusCode

    private suspend fun signedIn(activity: Activity): Boolean =
        PlayGames.getGamesSignInClient(activity).isAuthenticated().awaitOrNull()?.isAuthenticated == true

    /**
     * A Play Services [Task] as a suspending call, treating any failure as absence.
     *
     * Written out rather than pulled in with kotlinx-coroutines-play-services: that dependency
     * exists to provide this, and technical_requirements.md section 8 asks for restraint.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = withTimeoutOrNull(TASK_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    private companion object {
        /** Unused — the overlay reports nothing back — but the launch has to carry one. */
        const val REQUEST_LEADERBOARD = 9001

        /**
         * GamesClientStatusCodes does not expose this one as a constant in 20.1.2, though its own
         * getStatusCodeString still names it. Read out of the SDK's mapping table rather than
         * guessed.
         */
        const val CLIENT_RECONNECT_REQUIRED = 26502

        /**
         * A Play Services task that never calls back would otherwise suspend for ever, and a button
         * whose coroutine is still waiting looks exactly like a button that does nothing. Ten
         * seconds is long enough for a slow network and short enough to still be an answer.
         */
        const val TASK_TIMEOUT_MILLIS = 10_000L
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
