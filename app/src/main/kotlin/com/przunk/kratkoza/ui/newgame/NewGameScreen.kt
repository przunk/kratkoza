package com.przunk.kratkoza.ui.newgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes

/** All five levels, available from the start — project.md section 7 and section 32's hard rule. */
@Composable
fun NewGameScreen(
    viewModel: NewGameViewModel,
    onGameCreated: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.createdPuzzleId.collect(onGameCreated)
    }

    KratkozaScaffold(
        title = stringResource(R.string.new_game_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.new_game_choose_difficulty),
                    style = MaterialTheme.typography.titleMedium,
                )
                for (difficulty in Difficulty.entries) {
                    OutlinedButton(
                        onClick = { viewModel.startGame(difficulty) },
                        enabled = !state.isGenerating,
                        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT_DP.dp),
                    ) {
                        Text(stringResource(difficulty.labelRes()))
                    }
                }
            }

            if (state.failed) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissFailure,
                    title = { Text(stringResource(R.string.new_game_failed_title)) },
                    text = { Text(stringResource(R.string.new_game_failed_message)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissFailure) {
                            Text(stringResource(R.string.new_game_failed_dismiss))
                        }
                    },
                )
            }

            if (state.isGenerating) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.new_game_generating),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private const val BUTTON_HEIGHT_DP = 56
