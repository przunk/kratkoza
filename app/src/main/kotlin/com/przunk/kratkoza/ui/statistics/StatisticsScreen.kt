package com.przunk.kratkoza.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.progress.Progress
import com.przunk.kratkoza.domain.stats.DailyStatistics
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
    var confirmingReset by remember { mutableStateOf(false) }

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
                LevelCard(progress = statistics.progress, formatCount = integerFormat::format)
            }

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

            statistics.daily?.let { daily ->
                item { SectionTitle(stringResource(R.string.statistics_daily)) }
                item {
                    DailyCompletionCard(
                        statistics = daily,
                        formatCount = integerFormat::format,
                    )
                }
                item {
                    DailyScoreCard(
                        statistics = daily,
                        noneLabel = none,
                        formatCount = integerFormat::format,
                        formatDecimal = decimalFormat::format,
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { confirmingReset = true },
                    enabled = statistics.startedCount > 0,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.statistics_reset))
                }
            }
        }
    }

    if (confirmingReset) {
        // Deleting a record cannot be undone, so it is asked for twice.
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.statistics_reset_title)) },
            text = { Text(stringResource(R.string.statistics_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetStatistics()
                        confirmingReset = false
                    },
                ) {
                    Text(stringResource(R.string.statistics_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) {
                    Text(stringResource(R.string.statistics_reset_cancel))
                }
            },
        )
    }
}

@Composable
private fun DailyCompletionCard(
    statistics: DailyStatistics,
    formatCount: (Number) -> String,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            StatRow(
                stringResource(R.string.statistics_daily_completed),
                formatCount(statistics.completedCount),
            )
            Text(
                text = stringResource(R.string.statistics_daily_by_difficulty),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            for (level in statistics.perDifficulty) {
                StatRow(
                    stringResource(level.difficulty.labelRes()),
                    formatCount(level.completedCount),
                )
            }
        }
    }
}

@Composable
private fun DailyScoreCard(
    statistics: DailyStatistics,
    noneLabel: String,
    formatCount: (Number) -> String,
    formatDecimal: (Number) -> String,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.statistics_daily_scores),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatRow(
                stringResource(R.string.statistics_daily_total_score),
                statistics.totalScore?.let(formatCount) ?: noneLabel,
            )
            StatRow(
                stringResource(R.string.statistics_daily_best_score),
                statistics.bestScore?.let(formatCount) ?: noneLabel,
            )
            StatRow(
                stringResource(R.string.statistics_daily_average_score),
                statistics.averageScore?.let(formatDecimal) ?: noneLabel,
            )
            StatRow(
                stringResource(R.string.statistics_daily_ranked_eligible),
                formatCount(statistics.rankedEligibleCount),
            )
        }
    }
}

/**
 * Level and progress towards the next (project.md section 32).
 *
 * A badge, and only a badge. Section 32 forbids XP from gating difficulties, candidates, settings or
 * any part of ordinary play — so this card reports, and nothing anywhere reads it to decide what the
 * player may do.
 */
@Composable
private fun LevelCard(progress: Progress, formatCount: (Number) -> String) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.statistics_level, progress.level),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.statistics_xp_total, formatCount(progress.totalXp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            LinearProgressIndicator(
                progress = { progress.fractionIntoLevel },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            // Spelled out beside the bar: a bar alone says roughly how far, never how much is left,
            // and it is unreadable to anyone who cannot see it at all.
            Text(
                text = stringResource(
                    R.string.statistics_xp_into_level,
                    formatCount(progress.xpIntoLevel),
                    formatCount(progress.xpForNextLevel),
                    progress.level + 1,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            // Only shown where there is something to show: a row of zeros on four levels the player
            // has no game waiting at is noise between the numbers they came here to read.
            if (statistics.inProgressCount > 0) {
                StatRow(
                    stringResource(R.string.statistics_in_progress),
                    formatCount(statistics.inProgressCount),
                )
            }
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
        // A label and its value are one statement. Merging them stops a screen reader from
        // announcing a run of detached labels followed by a run of unexplained numbers.
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
