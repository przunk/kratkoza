package com.przunk.kratkoza.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.util.formatDuration

/**
 * The entry point.
 *
 * Continue comes first and reads "Continue — Expert — 12:43", exactly as project.md section 22 asks,
 * so resuming is one tap from launch.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onContinue: (String) -> Unit,
    onNewGame: () -> Unit,
    onDaily: () -> Unit,
    onSavedGames: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    KratkozaScaffold(title = stringResource(R.string.home_title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MENU_GAP_DP.dp),
        ) {
            // The artwork sits in the space above the menu and hangs from the top of it, so it
            // stays clear of the buttons instead of crowding down onto them. The weights leave the
            // menu near the middle — a little below it, not pushed to the bottom.
            Box(
                modifier = Modifier.fillMaxWidth().weight(SPACE_ABOVE_MENU),
                contentAlignment = Alignment.TopCenter,
            ) {
                RotatingBoardArt(modifier = Modifier.fillMaxWidth())
            }

            val mostRecent = state.mostRecent
            if (mostRecent != null) {
                // Taller than the rest: it carries two lines, and a height that fits one would
                // clip the second.
                Button(
                    onClick = { onContinue(mostRecent.puzzleId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CONTINUE_HEIGHT_DP.dp),
                ) {
                    MenuButtonContent(painterResource(R.drawable.ic_play)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.home_continue),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.home_continue_details,
                                    stringResource(mostRecent.difficulty.labelRes()),
                                    formatDuration(context, mostRecent.elapsedMillis),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            MenuButton(
                label = stringResource(R.string.home_new_game),
                painter = painterResource(R.drawable.ic_add),
                onClick = onNewGame,
            )
            MenuButton(
                label = stringResource(R.string.home_daily),
                painter = painterResource(R.drawable.ic_daily),
                onClick = onDaily,
            )
            MenuButton(
                label = stringResource(R.string.home_saved_games),
                painter = painterResource(R.drawable.ic_saved_games),
                onClick = onSavedGames,
            )
            MenuButton(
                label = stringResource(R.string.home_statistics),
                painter = painterResource(R.drawable.ic_statistics),
                onClick = onStatistics,
            )
            MenuButton(
                label = stringResource(R.string.home_settings),
                painter = painterResource(R.drawable.ic_settings),
                onClick = onSettings,
            )

            Spacer(Modifier.weight(SPACE_BELOW_MENU))
        }
    }
}

/**
 * A menu entry.
 *
 * The icon is decoration, not information: every entry already says in words what it does, so it
 * carries a null content description rather than repeating the label to a screen reader.
 */
@Composable
private fun MenuButton(label: String, painter: Painter, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT_DP.dp),
    ) {
        MenuButtonContent(painter) { Text(label) }
    }
}

/**
 * Icon pinned to the left edge, label dead centre.
 *
 * Laying these out as one centred row instead would put the *label* off-centre by half the icon's
 * width — which is what made the Continue button look nudged to the right. Centring each label on
 * its own and letting the icons follow would leave the icons stepping about down the menu. Placing
 * them independently is what gets both: a tidy column of icons and every label on the screen's
 * centre line.
 *
 * The label keeps a gap wide enough for the icon on *both* sides, so a long translation runs out of
 * room symmetrically rather than sliding under the icon.
 */
@Composable
private fun MenuButtonContent(painter: Painter, label: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Box(modifier = Modifier.padding(horizontal = LABEL_INSET_DP.dp)) { label() }
    }
}

/** Clear of the icon on either side, so a centred label never slides under it. */
private const val LABEL_INSET_DP = 40

private const val BUTTON_HEIGHT_DP = 60

/** Two lines of label plus the button's own padding; one line's height would clip the second. */
private const val CONTINUE_HEIGHT_DP = 76

private const val MENU_GAP_DP = 12

/** Slightly more room above the menu than below, so it sits a touch under the middle. */
private const val SPACE_ABOVE_MENU = 1.25f
private const val SPACE_BELOW_MENU = 0.9f
