package com.przunk.kratkoza.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.util.formatDuration

/**
 * The game screen (project.md section 37: the board comes first, everything else serves it).
 *
 * Layout in portrait, top to bottom: a slim status row, the board, the number pad, the actions. The
 * board keeps its 1:1 aspect and the full width; nothing else is allowed to push it around.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Nothing to play: the save was deleted from the saved-games list, or could not be read back.
    LaunchedEffect(state.phase) {
        if (state.phase == GameUiState.Phase.MISSING) onNavigateBack()
    }

    // project.md section 20: going to the background pauses the game, and is the last safe moment
    // to write the clock out before the process may be killed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onLeftForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.difficulty.labelRes())) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SCREEN_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP_DP.dp),
        ) {
            when (state.phase) {
                GameUiState.Phase.LOADING, GameUiState.Phase.MISSING -> LoadingBody()
                else -> PlayingBody(state = state, viewModel = viewModel)
            }
        }
    }

    if (state.phase == GameUiState.Phase.SOLVED) {
        SolvedDialog(state = state, onBackToMenu = onNavigateBack, onNewGame = onNewGame)
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.game_loading),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PlayingBody(state: GameUiState, viewModel: GameViewModel) {
    GameStatusRow(state = state, onTogglePause = { viewModel.setPaused(!state.isPaused) })

    Box {
        SudokuBoardView(
            board = state.board,
            givens = state.givens,
            candidates = state.candidates,
            incorrectCells = state.incorrectCells,
            selectedCell = state.selectedCell,
            peerCells = state.peerCells,
            sameDigitCells = state.sameDigitCells,
            onCellClick = viewModel::selectCell,
            enabled = state.isPlayable,
        )

        // project.md section 20: the board must be covered while the clock is stopped, so a pause
        // cannot be used to study it.
        if (state.isPaused) {
            PauseCover(
                onResume = { viewModel.setPaused(false) },
                modifier = Modifier.matchParentSize(),
            )
        }
    }

    NumberPad(
        remainingByDigit = state.remainingByDigit,
        onDigitClick = viewModel::enterDigit,
        enabled = state.isPlayable,
        candidateMode = state.candidateMode,
        showRemainingCounts = state.showRemainingCounts,
    )

    GameActionsRow(
        canUndo = state.canUndo,
        candidateMode = state.candidateMode,
        enabled = state.isPlayable,
        onUndo = viewModel::undo,
        onErase = viewModel::erase,
        onToggleCandidates = viewModel::toggleCandidateMode,
    )
}

/** Time, mistakes and the pause button — the only numbers project.md section 34 lets compete
 *  with the board for attention. */
@Composable
private fun GameStatusRow(state: GameUiState, onTogglePause: () -> Unit) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelledValue(
            label = stringResource(R.string.game_time),
            value = formatDuration(context, state.elapsedMillis),
        )
        LabelledValue(
            label = stringResource(R.string.game_mistakes),
            value = stringResource(R.string.game_mistakes_value, state.mistakeCount),
        )
        TextButton(onClick = onTogglePause) {
            Text(
                stringResource(
                    if (state.isPaused) R.string.game_resume else R.string.game_pause,
                ),
            )
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PauseCover(onResume: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.game_paused_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.game_paused_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Button(onClick = onResume) { Text(stringResource(R.string.game_resume)) }
        }
    }
}

@Composable
private fun GameActionsRow(
    canUndo: Boolean,
    candidateMode: Boolean,
    enabled: Boolean,
    onUndo: () -> Unit,
    onErase: () -> Unit,
    onToggleCandidates: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ACTION_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(ACTION_GAP_DP.dp),
    ) {
        OutlinedButton(
            onClick = onUndo,
            enabled = enabled && canUndo,
            modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            Text(stringResource(R.string.game_undo))
        }
        OutlinedButton(
            onClick = onErase,
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            Text(stringResource(R.string.game_erase))
        }
        // Candidate mode reads as on or off from its label as well as its fill, so the state is not
        // carried by colour alone (project.md section 8).
        FilledTonalButton(
            onClick = onToggleCandidates,
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            Text(
                stringResource(
                    if (candidateMode) R.string.game_candidates_on else R.string.game_candidates_off,
                ),
            )
        }
    }
}

@Composable
private fun SolvedDialog(state: GameUiState, onBackToMenu: () -> Unit, onNewGame: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        // Not dismissible by tapping outside: the game is over and the two choices are the only
        // things left to do here.
        onDismissRequest = onBackToMenu,
        title = { Text(stringResource(R.string.game_solved_title)) },
        text = {
            Text(
                stringResource(
                    R.string.game_solved_message,
                    stringResource(state.difficulty.labelRes()),
                    formatDuration(context, state.elapsedMillis),
                    pluralStringResource(
                        R.plurals.mistake_count,
                        state.mistakeCount,
                        state.mistakeCount,
                    ),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onNewGame) { Text(stringResource(R.string.game_solved_new)) }
        },
        dismissButton = {
            TextButton(onClick = onBackToMenu) { Text(stringResource(R.string.game_solved_home)) }
        },
    )
}

private const val SCREEN_PADDING_DP = 12
private const val SECTION_GAP_DP = 12
private const val ACTION_HEIGHT_DP = 52
private const val ACTION_GAP_DP = 8
