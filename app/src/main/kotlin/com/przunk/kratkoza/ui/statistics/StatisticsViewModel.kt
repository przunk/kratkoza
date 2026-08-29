package com.przunk.kratkoza.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.przunk.kratkoza.data.repository.StatisticsRepository
import com.przunk.kratkoza.domain.stats.Statistics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** project.md section 33 — the player's own record, straight from the repository. */
class StatisticsViewModel(private val statisticsRepository: StatisticsRepository) : ViewModel() {

    val statistics: StateFlow<Statistics> = statisticsRepository.statistics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = Statistics.EMPTY,
        )

    /**
     * Clears the record. Games still in progress keep counting — see
     * [GameRepository.resetStatistics].
     */
    fun resetStatistics() {
        viewModelScope.launch { statisticsRepository.resetStatistics() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
