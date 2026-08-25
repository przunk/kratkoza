package com.przunk.kratkoza.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

            MenuButton(stringResource(R.string.home_new_game), onNewGame)
            MenuButton(stringResource(R.string.home_saved_games), onSavedGames)
            MenuButton(stringResource(R.string.home_statistics), onStatistics)
            MenuButton(stringResource(R.string.home_settings), onSettings)
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT_DP.dp),
    ) {
        Text(label)
    }
}

private const val BUTTON_HEIGHT_DP = 60
