package com.przunk.kratkoza.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

    // No title bar here, unlike every other screen: the artwork draws the name itself, and a bar
    // above it would repeat the word and cut the scene short. Without a bar nothing reserves the
    // status bar, so the inset is taken directly.
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                // Room above the artwork. Without a title bar the scene otherwise begins at the
                // very edge of the usable area, which reads as cramped rather than as full-bleed.
                .padding(top = TOP_MARGIN_DP.dp),
            verticalArrangement = Arrangement.spacedBy(MENU_GAP_DP.dp),
        ) {
            // The artwork sits in the space above the menu and hangs from the top of it, so it
            // stays clear of the buttons instead of crowding down onto them. The weights leave the
            // menu near the middle — a little below it, not pushed to the bottom.
            // Measured, not weighted. The rhythm down the screen is margin, title, margin, board,
            // twice a margin, menu — which only holds if the artwork takes the height it needs
            // instead of stretching into whatever is left.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                RotatingBoardArt(
                    // Clear of the menu below. The scene tilts and its particles drift past its
                    // own bounds, so without a gap the artwork ends up touching the first button.
                    modifier = Modifier.fillMaxWidth().padding(bottom = ART_MENU_GAP_DP.dp),
                    contentDescription = stringResource(R.string.home_title),
                )
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

/** Breathing room under the status bar, in place of the title bar that used to provide it. */
private const val TOP_MARGIN_DP = 20

/** Between the artwork and the first button. */
private const val ART_MENU_GAP_DP = 24

private const val SPACE_BELOW_MENU = 0.75f
