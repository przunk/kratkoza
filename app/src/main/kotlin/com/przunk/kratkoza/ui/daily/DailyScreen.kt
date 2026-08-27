package com.przunk.kratkoza.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import java.time.LocalDate
import java.time.ZoneId
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
                            changeoverText(state.date ?: LocalDate.now()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(state.entries, key = { it.puzzleId }) { entry ->
                DailyCard(
                    entry = entry,
                    onOpen = { viewModel.open(entry.difficulty) },
                    // The board is worth showing whether or not today's attempt is done; it is the
                    // only place the player can see where the score actually landed.
                    onShowLeaderboard = if (state.signedIn) {
                        { viewModel.showLeaderboard(entry.difficulty) }
                    } else {
                        null
                    },
                )
            }

            // Only once there is something to show. An empty results card on a day nobody has
            // played is a promise of information rather than information.
            if (state.entries.any { it.score != null }) {
                item { DailyResults(entries = state.entries) }
            }

            // Below the boards, not above them: signing in is optional, and the day's puzzles are
            // what the screen is for. Absent entirely when Play Games is not configured.
            if (state.rankingAvailable && !state.signedIn) {
                item { SignInCard(onSignIn = viewModel::signIn) }
            }
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.daily_sign_in_explained),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onSignIn) {
                    Text(stringResource(R.string.daily_sign_in))
                }
            }
        }
    }
}

/**
 * The changeover hour in the reader's own clock and format (project.md section 4).
 *
 * Derived from the day being shown, because the local hour of a fixed UTC instant moves with
 * daylight saving — a constant here would be wrong for half the year.
 */
@Composable
private fun changeoverText(date: LocalDate): String = remember(date) {
    DailySchedule.localChangeover(date, ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

@Composable
private fun DailyCard(
    entry: DailyEntry,
    onOpen: () -> Unit,
    /** `null` when there is no ranking to open — the button is absent rather than dead. */
    onShowLeaderboard: (() -> Unit)?,
) {
    val context = LocalContext.current
    val solved = entry.status == DailyStatus.COMPLETED

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(entry.difficulty.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // A solved board has no button, so without this the card would be the only one on
                // the screen with nothing on its right — read as missing rather than as finished.
                if (solved) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.daily_solved_mark),
                        tint = SolvedTick,
                        modifier = Modifier.size(TICK_SIZE_DP.dp),
                    )
                }
                if (onShowLeaderboard != null) {
                    IconButton(onClick = onShowLeaderboard) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ranked),
                            contentDescription = stringResource(R.string.daily_leaderboard),
                        )
                    }
                }
            }
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

/**
 * The day's own scoreboard (project.md section 29).
 *
 * Local only. Comparing against other players waits for the leaderboards, but a player's own three
 * results are already here and are most of what the card is for — seeing today at a glance.
 */
@Composable
private fun DailyResults(entries: List<DailyEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.daily_results_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            for (entry in entries) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(entry.difficulty.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = entry.score
                            ?.let { stringResource(R.string.daily_score, it) }
                            ?: stringResource(R.string.daily_results_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.score == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.daily_results_total),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    // Unfinished boards contribute nothing rather than being left out, so the total
                    // is the day's score and not the score of the parts that went well.
                    text = stringResource(R.string.daily_score, entries.sumOf { it.score ?: 0 }),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/**
 * Green for a finished board.
 *
 * Fixed rather than taken from the palette: green means done here, and a palette that recoloured it
 * would leave the tick agreeing with the board and disagreeing with everybody's idea of a tick. The
 * label beside it carries the same meaning in words, so nothing rests on the colour
 * (project.md section 8).
 */
private val SolvedTick = Color(0xFF2E7D46)

private const val TICK_SIZE_DP = 22

@Composable
private fun statusText(entry: DailyEntry, context: android.content.Context): String = when (entry.status) {
    DailyStatus.NOT_STARTED -> stringResource(R.string.daily_status_not_started)
    DailyStatus.IN_PROGRESS -> stringResource(
        R.string.daily_status_in_progress,
        formatDuration(context, entry.elapsedMillis ?: 0),
    )
    DailyStatus.COMPLETED -> {
        val time = formatDuration(context, entry.elapsedMillis ?: 0)
        val mistakes = pluralStringResource(
            R.plurals.mistake_count,
            entry.mistakeCount ?: 0,
            entry.mistakeCount ?: 0,
        )
        entry.score
            ?.let { stringResource(R.string.daily_status_completed_scored, time, mistakes, it) }
            ?: stringResource(R.string.daily_status_completed, time, mistakes)
    }
}
