package com.przunk.kratkoza.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.daily.DailyEntry
import com.przunk.kratkoza.domain.daily.DailySchedule
import com.przunk.kratkoza.domain.daily.DailyStatus
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.util.formatDuration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The day's three boards (project.md section 26).
 *
 * Everybody gets the same three, and they are the same for everybody without a server: the boards
 * are derived from the date. See `DailyPuzzles`.
 */
@Composable
fun DailyScreen(
    viewModel: DailyViewModel,
    onOpenGame: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openGame.collect(onOpenGame)
    }

    // Coming back from a board changes what the cards should say — a game may now be in progress,
    // finished, or have dropped out of the ranking. Without this the list keeps showing whatever it
    // knew when it was first opened, which is worse than showing nothing.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    KratkozaScaffold(
        title = stringResource(R.string.daily_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        if (state.isLoading && state.entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.daily_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return@KratkozaScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.daily_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Both the countdown and the wall-clock time: one answers "how long have
                        // I got", the other "when exactly", and neither substitutes for the other.
                        text = stringResource(
                            R.string.daily_next_in_at,
                            formatDuration(context, state.remainingMillis),
                            changeoverText(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(state.entries, key = { it.puzzleId }) { entry ->
                DailyCard(entry = entry, onOpen = { viewModel.open(entry.difficulty) })
            }
        }
    }
}

/** The changeover hour in the reader's own clock format (project.md section 4). */
@Composable
private fun changeoverText(): String = remember {
    DailySchedule.CHANGEOVER.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

@Composable
private fun DailyCard(entry: DailyEntry, onOpen: () -> Unit) {
    val context = LocalContext.current
    val solved = entry.status == DailyStatus.COMPLETED

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(entry.difficulty.labelRes()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = statusText(entry, context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            // project.md section 28. Said plainly on the card rather than discovered when the pause
            // button turns out to be missing.
            Text(
                text = stringResource(
                    if (entry.rankedEligible) R.string.daily_ranked else R.string.daily_unranked,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A solved board offers no button at all. One attempt is the point of a Daily, and
            // a control that refuses when pressed is worse than one that is not there.
            if (!solved) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onOpen) {
                        Text(
                            stringResource(
                                if (entry.status == DailyStatus.IN_PROGRESS) {
                                    R.string.daily_continue
                                } else {
                                    R.string.daily_open
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(entry: DailyEntry, context: android.content.Context): String = when (entry.status) {
    DailyStatus.NOT_STARTED -> stringResource(R.string.daily_status_not_started)
    DailyStatus.IN_PROGRESS -> stringResource(
        R.string.daily_status_in_progress,
        formatDuration(context, entry.elapsedMillis ?: 0),
    )
    DailyStatus.COMPLETED -> stringResource(
        R.string.daily_status_completed,
        formatDuration(context, entry.elapsedMillis ?: 0),
        pluralStringResource(R.plurals.mistake_count, entry.mistakeCount ?: 0, entry.mistakeCount ?: 0),
    )
}
