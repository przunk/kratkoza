package com.przunk.kratkoza.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.przunk.kratkoza.data.local.KratkozaDatabase
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.data.repository.SettingsRepository
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlinx.coroutines.Dispatchers

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

    val gameRepository: GameRepository by lazy {
        GameRepository(
            puzzleDao = database.puzzleDao(),
            savedGameDao = database.savedGameDao(),
            historyDao = database.gameHistoryDao(),
            generator = SudokuGenerator(),
            computationDispatcher = Dispatchers.Default,
        )
    }
}

/**
 * The container, reachable from the composition.
 *
 * No default: a screen built outside [com.przunk.kratkoza.KratkozaApplication] has no graph to read,
 * and failing at that point is far easier to diagnose than a silently empty database.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer requested outside of the Kratkoza application")
}
