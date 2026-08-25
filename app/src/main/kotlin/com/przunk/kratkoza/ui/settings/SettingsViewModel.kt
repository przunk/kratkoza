package com.przunk.kratkoza.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.SettingsRepository
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** project.md section 35. Each setter writes and lets the store's own flow report the change back. */
class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<GameSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GameSettings.DEFAULT,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
