package com.przunk.kratkoza.di

import android.content.Context
import com.przunk.kratkoza.data.local.KratkozaDatabase
import com.przunk.kratkoza.data.ranking.ActivityHolder
import com.przunk.kratkoza.data.ranking.PlayGamesConfig
import com.przunk.kratkoza.data.ranking.PlayGamesRanking
import com.przunk.kratkoza.data.ranking.RankingSubmitter
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.data.repository.SettingsRepository
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.RankingLog
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The whole object graph, built by hand (technical_requirements.md section 8).
 *
 * Everything is lazy so that opening the database is paid for by the first screen that needs it,
 * not by `Application.onCreate`.
 *
 * A framework would buy nothing at this size: there are four objects and one wiring, all visible in
 * twenty lines. When that stops being true, this is the file that says so.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    private val database: KratkozaDatabase by lazy { KratkozaDatabase.create(applicationContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(applicationContext) }

    /**
     * The Activity the Play Games SDK asks for. MainActivity puts itself here; nothing else should.
     */
    val activities = ActivityHolder()

    /** What the ranking has been doing, for the debug panel on the Daily screen. */
    val rankingLog = RankingLog()

    /**
     * Work that must outlive the screen that started it.
     *
     * Sending a finished Daily to the boards is three network round trips, and the player is
     * usually already walking away from the solved puzzle. A SupervisorJob so that one failed
     * submission does not take the others with it.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Sends finished Dailies to the public boards; see [RankingSubmitter] for why it lives here. */
    val rankingSubmitter: RankingSubmitter by lazy {
        RankingSubmitter(gameRepository, ranking, rankingLog, applicationScope)
    }

    /** The public ranking (project.md section 27), inert until Play Console identifiers are filled in. */
    val ranking: Ranking by lazy {
        PlayGamesRanking(PlayGamesConfig(applicationContext), activities)
    }

    val gameRepository: GameRepository by lazy {
        GameRepository(
            puzzleDao = database.puzzleDao(),
            dailyPuzzleDao = database.dailyPuzzleDao(),
            savedGameDao = database.savedGameDao(),
            historyDao = database.gameHistoryDao(),
            generator = SudokuGenerator(),
            computationDispatcher = Dispatchers.Default,
        )
    }
}
