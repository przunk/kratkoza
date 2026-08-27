package com.przunk.kratkoza.data.ranking

import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.FinishedGame
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.ranking.DailyResult
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.RankingLog
import com.przunk.kratkoza.domain.ranking.SubmissionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sends a finished Daily to the public boards, outliving the screen it was finished on.
 *
 * This exists because putting the work in the game screen's ViewModel was wrong. Three submissions
 * are three network round trips, and a player who leaves the solved board goes back to the Daily
 * list — which clears that ViewModel and cancels whatever it was still doing. The result was scores
 * that arrived or not depending on how long somebody looked at a finished puzzle: the first
 * submission always landed, the third almost never.
 *
 * The scope handed in here belongs to the application, so the sequence finishes on its own terms.
 * Nothing is waiting for it: the game is already recorded, and the ranking is the part that can be
 * late.
 */
class RankingSubmitter(
    private val repository: GameRepository,
    private val ranking: Ranking,
    private val log: RankingLog,
    private val scope: CoroutineScope,
) {

    /** Hands over a finished game. Returns at once; the sending happens on the application's scope. */
    fun submit(finished: FinishedGame, puzzleId: String) {
        scope.launch { send(finished, puzzleId) }
    }

    private suspend fun send(finished: FinishedGame, puzzleId: String) {
        // Each way out says so. Silence used to cover all of them at once, which made "no entry"
        // mean anything from "this was a solo game" to "the code never ran".
        if (finished.gameType != GameType.DAILY) {
            log.add("ranking: skipped — solo games are not ranked")
            return
        }
        if (!finished.rankedEligible) {
            log.add("ranking: skipped — this daily left the ranking")
            return
        }
        val score = finished.score ?: run {
            log.add("ranking: skipped — no score was recorded")
            return
        }
        if (!ranking.isAvailable) {
            log.add("ranking: skipped — Play Games is not configured")
            return
        }
        if (Leaderboard.forDaily(finished.difficulty) == null) {
            log.add("ranking: skipped — no board for ${finished.difficulty}")
            return
        }
        val totals = repository.dailyTotals(puzzleId) ?: run {
            log.add("ranking: skipped — $puzzleId is not a daily board")
            return
        }

        // Every board is asked what it wants from this result, rather than the sending order being
        // written out here. A board added to the enum is picked up by this loop without touching
        // this file — which is the whole reason the declaration lives in one place.
        val result = DailyResult(finished.difficulty, score, totals)
        for (board in Leaderboard.entries) {
            val value = board.valueFor(result) ?: continue
            val outcome = sendOne(board, value)

            if (board.carriesOwnResult && outcome is SubmissionOutcome.Submitted) {
                repository.markPubliclyRanked(finished.historyId)
            }
        }
    }

    private suspend fun sendOne(board: Leaderboard, value: Long): SubmissionOutcome {
        val outcome = ranking.submit(board, value)
        log.add(
            when (outcome) {
                is SubmissionOutcome.Submitted -> if (outcome.newBest) {
                    "ranking: sent $value to $board — new best"
                } else {
                    // Not a failure. A leaderboard keeps the best score, so a worse one is accepted
                    // and changes nothing; without saying so, "sent" next to an unchanged board
                    // reads as the app lying.
                    "ranking: sent $value to $board — previous score was better"
                }
                is SubmissionOutcome.Queued -> "ranking: queued $value to $board (${outcome.detail})"
                is SubmissionOutcome.Failed -> "ranking: $board — ${outcome.detail}"
            },
        )
        return outcome
    }
}
