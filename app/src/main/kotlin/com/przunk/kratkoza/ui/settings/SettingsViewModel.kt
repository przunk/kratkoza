package com.przunk.kratkoza.ui.settings

import androidx.lifecycle.ViewModel
import com.przunk.kratkoza.domain.ranking.RankingLog
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.SettingsRepository
import com.przunk.kratkoza.domain.settings.AppLanguage
import com.przunk.kratkoza.domain.settings.BoardPalette
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** project.md section 35. Each setter writes and lets the store's own flow report the change back. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    /**
     * What the ranking has been doing. Read-only here — Settings is where somebody goes when
     * something is wrong, which is the only place this belongs once an investigation is over.
     */
    rankingLog: RankingLog = RankingLog(),
) : ViewModel() {

    val rankingDiagnostics: StateFlow<List<String>> = rankingLog.entries

    val settings: StateFlow<GameSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GameSettings.DEFAULT,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage, onApplied: () -> Unit = {}) {
        if (language == settings.value.language) return
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            onApplied()
        }
    }

    fun setPalette(palette: BoardPalette) {
        viewModelScope.launch { settingsRepository.setPalette(palette) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHighContrast(enabled) }
    }

    fun setAutoRemoveCandidates(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoRemoveCandidates(enabled) }
    }

    fun setHighlightSameDigits(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHighlightSameDigits(enabled) }
    }

    fun setShowRemainingCounts(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowRemainingCounts(enabled) }
    }

    fun setShowEightOfNineHint(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowEightOfNineHint(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
