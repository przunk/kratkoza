package com.przunk.kratkoza.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.stats.DifficultyStatistics
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.util.formatDuration
import java.text.NumberFormat

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val none = stringResource(R.string.statistics_none)

    // Locale-aware throughout: project.md section 4 asks for numbers and percentages to be
    // formatted for the reader, not for the developer.
    val percentFormat = NumberFormat.getPercentInstance()
    val decimalFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    val integerFormat = NumberFormat.getIntegerInstance()

    KratkozaScaffold(
        title = stringResource(R.string.statistics_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        SectionTitle(stringResource(R.string.statistics_overall))
                        StatRow(
                            stringResource(R.string.statistics_started),
                            integerFormat.format(statistics.startedCount),
                        )
                        StatRow(
                            stringResource(R.string.statistics_completed),
                            integerFormat.format(statistics.completedCount),
                        )
                        StatRow(
                            stringResource(R.string.statistics_completion_rate),
                            percentFormat.format(statistics.completionRate),
                        )
                        StatRow(
                            stringResource(R.string.statistics_total_mistakes),
                            integerFormat.format(statistics.totalMistakes),
                        )
                        StatRow(
                            stringResource(R.string.statistics_average_mistakes),
                            statistics.averageMistakes?.let(decimalFormat::format) ?: none,
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.statistics_by_difficulty)) }

            items(statistics.perDifficulty.size) { position ->
                DifficultyCard(
                    statistics = statistics.perDifficulty[position],
                    noneLabel = none,
                    formatTime = { millis -> formatDuration(context, millis) },
                    formatCount = integerFormat::format,
                    formatDecimal = decimalFormat::format,
                )
            }
        }
    }
}

@Composable
private fun DifficultyCard(
    statistics: DifficultyStatistics,
    noneLabel: String,
    formatTime: (Long) -> String,
    formatCount: (Number) -> String,
    formatDecimal: (Number) -> String,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(statistics.difficulty.labelRes()),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow(
                stringResource(R.string.statistics_started),
                formatCount(statistics.startedCount),
            )
            StatRow(
                stringResource(R.string.statistics_completed),
                formatCount(statistics.completedCount),
            )
            StatRow(
                stringResource(R.string.statistics_best_time),
                statistics.bestTimeMillis?.let(formatTime) ?: noneLabel,
            )
            StatRow(
                stringResource(R.string.statistics_average_time),
                statistics.averageTimeMillis?.let(formatTime) ?: noneLabel,
            )
            StatRow(
                stringResource(R.string.statistics_average_mistakes),
                statistics.averageMistakes?.let(formatDecimal) ?: noneLabel,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
