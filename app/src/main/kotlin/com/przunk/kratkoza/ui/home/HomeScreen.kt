package com.przunk.kratkoza.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            val mostRecent = state.mostRecent
            if (mostRecent != null) {
                Button(
                    onClick = { onContinue(mostRecent.puzzleId) },
                    modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT_DP.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.width(16.dp))
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

            MenuButton(
                label = stringResource(R.string.home_new_game),
                painter = painterResource(R.drawable.ic_add),
                onClick = onNewGame,
            )
            MenuButton(
                label = stringResource(R.string.home_saved_games),
                painter = painterResource(R.drawable.ic_list),
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
private fun MenuButton(label: String, painter: Painter, onClick: () -> Unit) =
    MenuButtonRow(label, onClick) { Icon(painter = painter, contentDescription = null) }

@Composable
private fun MenuButtonRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT_DP.dp),
    ) {
        icon()
        // A fixed gap after the icon rather than a centred row: the labels differ in length, and
        // centring each one separately would leave the icons dancing about down the menu.
        Spacer(Modifier.width(16.dp))
        Text(text = label, modifier = Modifier.weight(1f))
    }
}

private const val BUTTON_HEIGHT_DP = 60
