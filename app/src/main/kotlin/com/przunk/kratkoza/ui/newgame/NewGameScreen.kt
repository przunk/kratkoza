package com.przunk.kratkoza.ui.newgame

import android.widget.Toast

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var importing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Resolved through stringResource, not context.getString: the composition follows the app's
    // configuration, a bare Context does not, and a language change would leave it handing out the
    // previous locale's text.
    val resumedMessage = stringResource(R.string.new_game_import_resumed)

    LaunchedEffect(viewModel) {
        viewModel.createdPuzzleId.collect { opened ->
            // Said before leaving, and as a toast rather than a dialog: a board opening half-filled
            // needs explaining, but not badly enough to put a button between the player and it.
            if (opened.resumed) {
                Toast.makeText(context, resumedMessage, Toast.LENGTH_LONG).show()
            }
            onGameCreated(opened.puzzleId)
        }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                TextButton(
                    onClick = { importing = true },
                    enabled = !state.isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.new_game_import))
                }
            }

            if (importing) {
                ImportBoardDialog(
                    onDismiss = { importing = false },
                    onOpen = { code ->
                        importing = false
                        viewModel.openSharedBoard(code)
                    },
                )
            }

            state.importProblem?.let { problem ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissFailure,
                    title = { Text(stringResource(R.string.new_game_import_title)) },
                    text = { Text(stringResource(problem.messageRes())) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissFailure) {
                            Text(stringResource(R.string.new_game_failed_dismiss))
                        }
                    },
                )
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

/**
 * Asks for a board code.
 *
 * The field takes whatever was pasted, including the grouping dashes, stray spaces and a quoted
 * line break or two — `BoardCode` strips all of that. Rejecting a code because it arrived with the
 * formatting it was sent with would be a strange thing to do to somebody who did nothing wrong.
 */
@Composable
private fun ImportBoardDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_game_import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.new_game_import_message))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.new_game_import_hint)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(code) }, enabled = code.isNotBlank()) {
                Text(stringResource(R.string.new_game_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.new_game_import_cancel))
            }
        },
    )
}

@StringRes
private fun ImportProblem.messageRes(): Int = when (this) {
    ImportProblem.NOT_A_CODE -> R.string.new_game_import_not_a_code
    ImportProblem.DAMAGED -> R.string.new_game_import_damaged
    ImportProblem.NOT_A_PUZZLE -> R.string.new_game_import_invalid
}

private const val BUTTON_HEIGHT_DP = 56
