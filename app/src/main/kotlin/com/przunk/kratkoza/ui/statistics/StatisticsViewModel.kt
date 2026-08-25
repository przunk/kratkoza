package com.przunk.kratkoza.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.stats.Statistics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** project.md section 33 — the player's own record, straight from the repository. */
class StatisticsViewModel(gameRepository: GameRepository) : ViewModel() {

    val statistics: StateFlow<Statistics> = gameRepository.statistics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = Statistics.EMPTY,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
