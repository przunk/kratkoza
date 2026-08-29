package com.przunk.kratkoza.data.ranking

import android.content.Context
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.ranking.Leaderboard

/**
 * The Play Games identifiers, and whether there are enough of them to try anything.
 *
 * They live in `res/values/play_games_ids.xml` rather than in code because they belong to one Play
 * Console project, not to the source: a second environment replaces the file and nothing else. It
 * is also the file to edit if the boards are ever replaced with fresh ones to clear test scores.
 *
 * Every identifier starts out empty. That is a state the app has to survive rather than assume
 * away, because the Play Games SDK initialises itself from its own ContentProvider before
 * `Application.onCreate` runs — there is no moment at which we could decline to start it. What the
 * app can do is refuse to *ask* it for anything, which is what [isConfigured] gates.
 */
class PlayGamesConfig(context: Context) {

    private val appId: String = context.getString(R.string.play_games_app_id)

    /**
     * Built by walking the enum rather than written out as a literal map, so a new board fails here,
     * loudly, instead of silently having no identifier at the moment a score is sent to it.
     */
    private val ids: Map<Leaderboard, String> = Leaderboard.entries.associateWith { board ->
        context.getString(
            when (board) {
                Leaderboard.DAILY_EASY -> R.string.leaderboard_daily_easy
                Leaderboard.DAILY_HARD -> R.string.leaderboard_daily_hard
                Leaderboard.DAILY_EXPERT -> R.string.leaderboard_daily_expert
                Leaderboard.DAILY_TOTAL -> R.string.leaderboard_daily_total
                Leaderboard.ALL_TIME_TOTAL -> R.string.leaderboard_all_time_total
            },
        )
    }

    /**
     * True only when the application identifier and every board are filled in.
     *
     * Partial configuration counts as unconfigured on purpose. Half a ranking — where one board
     * accepts scores and another silently does not — is harder to notice than no ranking at all.
     */
    val isConfigured: Boolean
        get() = appId.isNotBlank() && ids.values.none { it.isBlank() }

    /** The Play Games identifier for one board, or `null` when it has none configured. */
    fun idFor(board: Leaderboard): String? = ids[board]?.takeIf { it.isNotBlank() }
}
