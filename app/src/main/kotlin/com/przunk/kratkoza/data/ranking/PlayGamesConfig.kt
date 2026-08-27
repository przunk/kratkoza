package com.przunk.kratkoza.data.ranking

import android.content.Context
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.model.Difficulty

/**
 * The Play Games identifiers, and whether there are enough of them to try anything.
 *
 * They live in `res/values/play_games_ids.xml` rather than in code because they belong to one Play
 * Console project, not to the source: a second environment replaces the file and nothing else.
 *
 * Every identifier starts out empty. That is a state the app has to survive rather than assume
 * away, because the Play Games SDK initialises itself from its own ContentProvider before
 * `Application.onCreate` runs — there is no moment at which we could decline to start it. What the
 * app can do is refuse to *ask* it for anything, which is what [isConfigured] gates.
 */
class PlayGamesConfig(context: Context) {

    private val appId: String = context.getString(R.string.play_games_app_id)

    /**
     * Built from the Daily level list rather than written out as a literal map, so that adding a
     * fourth Daily level fails here, loudly, instead of showing up much later as scores that vanish
     * on one difficulty and nowhere else.
     */
    private val leaderboards: Map<Difficulty, String> =
        DailyPuzzles.DIFFICULTIES.associateWith { difficulty ->
            context.getString(
                when (difficulty) {
                    Difficulty.EASY -> R.string.leaderboard_daily_easy
                    Difficulty.HARD -> R.string.leaderboard_daily_hard
                    Difficulty.MASTER -> R.string.leaderboard_daily_master
                    else -> error("Daily runs at $difficulty but no leaderboard is mapped to it")
                },
            )
        }

    /**
     * True only when the application identifier and every Daily leaderboard are filled in.
     *
     * Partial configuration counts as unconfigured on purpose. Half a ranking — where one level
     * submits and another silently does not — is harder to notice than no ranking at all.
     */
    val isConfigured: Boolean
        get() = appId.isNotBlank() && leaderboards.values.none { it.isBlank() }

    /** The leaderboard for a Daily level, or `null` when that level has none configured. */
    fun leaderboardFor(difficulty: Difficulty): String? =
        leaderboards[difficulty]?.takeIf { it.isNotBlank() }
}
