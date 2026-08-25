package com.przunk.kratkoza.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.przunk.kratkoza.domain.settings.AppLanguage
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.settings.ThemeMode
import com.przunk.kratkoza.util.AppLocales
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User preferences in DataStore (technical_requirements.md section 6).
 *
 * Every value has a default, so a missing key is not an error — which is what makes adding an option
 * later a one-line change rather than a migration.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val dataStore = appContext.settingsDataStore

    val settings: Flow<GameSettings> = dataStore.data
        // A DataStore read can fail on a corrupt file. Falling back to defaults keeps the app
        // playable; refusing to emit would leave the UI waiting forever on its first frame.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences ->
            GameSettings(
                themeMode = preferences[THEME_MODE]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: GameSettings.DEFAULT.themeMode,
                language = preferences[LANGUAGE]
                    ?.let(AppLanguage::fromStorageName)
                    ?: if (!AppLocales.handledByApp) {
                        AppLocales.fromLocaleManager(appContext) ?: GameSettings.DEFAULT.language
                    } else {
                        GameSettings.DEFAULT.language
                    },
                autoRemoveCandidates = preferences[AUTO_REMOVE_CANDIDATES]
                    ?: GameSettings.DEFAULT.autoRemoveCandidates,
                highlightSameDigits = preferences[HIGHLIGHT_SAME_DIGITS]
                    ?: GameSettings.DEFAULT.highlightSameDigits,
                showRemainingCounts = preferences[SHOW_REMAINING_COUNTS]
                    ?: GameSettings.DEFAULT.showRemainingCounts,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        if (!AppLocales.handledByApp) {
            AppLocales.applyToSystem(appContext, language)
        }
        dataStore.edit { it[LANGUAGE] = language.name }
    }

    suspend fun setAutoRemoveCandidates(enabled: Boolean) {
        dataStore.edit { it[AUTO_REMOVE_CANDIDATES] = enabled }
    }

    suspend fun setHighlightSameDigits(enabled: Boolean) {
        dataStore.edit { it[HIGHLIGHT_SAME_DIGITS] = enabled }
    }

    suspend fun setShowRemainingCounts(enabled: Boolean) {
        dataStore.edit { it[SHOW_REMAINING_COUNTS] = enabled }
    }

    private companion object {
        // Stored by name, not by ordinal: an ordinal silently means something else the day an enum
        // entry is inserted in the middle.
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_REMOVE_CANDIDATES = booleanPreferencesKey("auto_remove_candidates")
        val HIGHLIGHT_SAME_DIGITS = booleanPreferencesKey("highlight_same_digits")
        val SHOW_REMAINING_COUNTS = booleanPreferencesKey("show_remaining_counts")
    }
}
