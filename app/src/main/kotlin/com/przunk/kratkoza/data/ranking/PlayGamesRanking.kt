package com.przunk.kratkoza.data.ranking

import android.app.Activity
import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.games.leaderboard.ScoreSubmissionData
import com.google.android.gms.tasks.Task
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.SignInOutcome
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

    /** Records both halves of authentication instead of guessing why the chooser disappeared. */
    override suspend fun signIn(): SignInOutcome {
        val activity = activities.current() ?: return SignInOutcome.Failed("no activity attached")
        val client = PlayGames.getGamesSignInClient(activity)

        val request = client.signIn()
        val response = request.awaitOrNull()
        val requestSnapshot = snapshot(request, response?.isAuthenticated)

        // The explicit request and the session it leaves behind are separate observations. Keeping
        // both catches the otherwise invisible case where the chooser completes but authentication
        // state does not change (or changes despite an unhelpful request result).
        val verification = client.isAuthenticated()
        val verificationResponse = verification.awaitOrNull()
        val verificationSnapshot = snapshot(verification, verificationResponse?.isAuthenticated)

        return resolveSignInOutcome(requestSnapshot, verificationSnapshot)
    }

    override suspend fun show(board: Leaderboard): LeaderboardOutcome {
        val leaderboard = config.idFor(board)
            ?: return LeaderboardOutcome.Failed("no leaderboard id for $board")
        val activity = activities.current()
            ?: return LeaderboardOutcome.Failed("no activity attached")

        // The overload that takes a span, not the one that leaves it to whatever the viewer last
        // looked at — which is how a daily board came to open on all-time.
        val request = PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(leaderboard, timeSpanOf(board))
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
    private fun describe(cause: Exception?, subject: String): String {
        // Play Games codes are five digits and mean nothing on their own; the SDK can name them,
        // and a name is what makes the difference between a bug report and a number.
        val reason = when (val code = statusCodeOf(cause)) {
            null -> cause?.let { "${it::class.java.simpleName}: ${it.message}" }
                ?: "no exception — timed out after ${TASK_TIMEOUT_MILLIS}ms"

            else -> "$code ${GamesClientStatusCodes.getStatusCodeString(code)}"
        }
        return "$subject — $reason"
    }

    override suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome {
        val leaderboard = config.idFor(board)
            ?: return SubmissionOutcome.Failed("no leaderboard id for $board")
        val activity = activities.current()
            ?: return SubmissionOutcome.Failed("no activity attached")
        if (!signedIn(activity)) return SubmissionOutcome.Failed("not signed in to Play Games")

        var request = submitOnce(activity, leaderboard, value)

        // CLIENT_RECONNECT_REQUIRED means the games client's session went stale between
        // authenticating and submitting — isAuthenticated answers yes while the connection behind
        // it no longer holds. Re-authenticating and trying once more is the first remedy.
        if (statusCodeOf(request.exception) == CLIENT_RECONNECT_REQUIRED) {
            PlayGames.getGamesSignInClient(activity).signIn().awaitOrNull()
            request = submitOnce(activity, leaderboard, value)
        }

        if (request.isSuccessful) {
            return SubmissionOutcome.Submitted(newBest = request.result?.isNewBest() ?: false)
        }

        // Nothing left to confirm with, so stop trying to confirm. submitScore returns void because
        // it does not report back at all: it queues the score inside Play Services, which delivers
        // it when the client is next healthy. That is exactly the situation here, and it is the
        // difference between a score that eventually lands and one that is thrown away for the sake
        // of a receipt.
        val reason = describe(request.exception, leaderboard)
        return runCatching {
            PlayGames.getLeaderboardsClient(activity).submitScore(leaderboard, value)
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
    /**
     * Whether the submission moved the all-time board.
     *
     * The all-time span is the one asked about because it is the board's own record of the player;
     * the daily and weekly spans answer a narrower question that the caller is not asking.
     */
    private fun ScoreSubmissionData.isNewBest(): Boolean =
        getScoreResult(LeaderboardVariant.TIME_SPAN_ALL_TIME)?.newBest == true

    private suspend fun submitOnce(
        activity: Activity,
        leaderboard: String,
        value: Long,
    ): Task<ScoreSubmissionData> {
        val request = PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(leaderboard, value)
        request.awaitOrNull()
        return request
    }

    private fun timeSpanOf(board: Leaderboard): Int = when (board.span) {
        Leaderboard.Span.TODAY -> LeaderboardVariant.TIME_SPAN_DAILY
        Leaderboard.Span.ALL_TIME -> LeaderboardVariant.TIME_SPAN_ALL_TIME
    }

    private fun statusCodeOf(cause: Exception?): Int? = (cause as? ApiException)?.statusCode

    private fun snapshot(task: Task<*>, authenticated: Boolean?): SignInTaskSnapshot {
        val cause = task.exception
        val statusCode = statusCodeOf(cause)
        return SignInTaskSnapshot(
            complete = task.isComplete,
            successful = task.isSuccessful,
            canceled = task.isCanceled,
            authenticated = authenticated,
            exceptionType = cause?.javaClass?.simpleName,
            statusCode = statusCode,
            statusName = statusCode?.let(GamesClientStatusCodes::getStatusCodeString),
            message = cause?.message,
        )
    }

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

/** A framework-free copy of the facts exposed by one Play Services task. */
internal data class SignInTaskSnapshot(
    val complete: Boolean,
    val successful: Boolean,
    val canceled: Boolean,
    val authenticated: Boolean?,
    val exceptionType: String? = null,
    val statusCode: Int? = null,
    val statusName: String? = null,
    val message: String? = null,
) {
    fun describe(label: String): String = buildString {
        append(label)
        append("{complete=").append(complete)
        append(", success=").append(successful)
        append(", canceled=").append(canceled)
        append(", authenticated=").append(authenticated ?: "none")
        if (exceptionType != null) append(", exception=").append(exceptionType)
        if (statusCode != null) {
            append(", status=").append(statusCode)
            if (statusName != null) append(' ').append(statusName)
        }
        if (!message.isNullOrBlank()) append(", message=").append(message)
        append('}')
    }
}

/** Decides from recorded facts only; the trace deliberately contains no proposed root cause. */
internal fun resolveSignInOutcome(
    request: SignInTaskSnapshot,
    verification: SignInTaskSnapshot,
): SignInOutcome {
    val detail = "${request.describe("request")}; ${verification.describe("verification")}"
    return when {
        request.authenticated == true || verification.authenticated == true ->
            SignInOutcome.Signed(detail)

        request.canceled || request.statusCode == CommonStatusCodes.CANCELED ->
            SignInOutcome.Dismissed(detail)

        else -> SignInOutcome.Failed(detail)
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
