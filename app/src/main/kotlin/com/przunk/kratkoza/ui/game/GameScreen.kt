package com.przunk.kratkoza.ui.game

import android.widget.Toast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.common.shareBoard
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

    // The moment eligibility is lost is the one worth saying out loud: it happens while the player
    // is looking at something else, and the only trace otherwise is a pause button that quietly
    // appeared. Fired on the transition, not on the state, so a game that was never ranked and one
    // that has already been told both stay silent.
    val context = LocalContext.current

    val lostMessage = stringResource(R.string.game_ranking_lost)
    var wasRanked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.rankedEligible) {
        if (wasRanked && !state.rankedEligible) {
            Toast.makeText(context, lostMessage, Toast.LENGTH_LONG).show()
        }
        wasRanked = state.rankedEligible
    }

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
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
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
                else -> PlayingBody(
                    state = state,
                    viewModel = viewModel,
                    onLeave = onNavigateBack,
                )
            }
        }
    }

    if (state.confirmingCandidateClear) {
        CandidateClearDialog(
            onConfirm = viewModel::confirmCandidateClear,
            onDismiss = viewModel::dismissCandidateClear,
        )
    }

    if (state.phase == GameUiState.Phase.SOLVED) {
        SolvedDialog(state = state, onBackToMenu = onNavigateBack, onNewGame = onNewGame)
    }
}

/**
 * Hands this board's code to another player (wishlist item 6).
 *
 * Sits in the game's own top bar because that is where somebody reaches for it — mid-puzzle, saying
 * "try this one". The saved-games list offers the same thing for boards not currently open.
 */
@Composable
private fun ShareBoardAction(difficulty: Difficulty, code: () -> String?) {
    val context = LocalContext.current
    val label = difficulty.labelRes()

    LabeledGameAction(
        label = stringResource(R.string.game_share),
        iconRes = R.drawable.ic_share,
        onClick = { code()?.let { shareBoard(context, label, it) } },
    )
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
private fun PlayingBody(state: GameUiState, viewModel: GameViewModel, onLeave: () -> Unit) {
    val haptics = LocalHapticFeedback.current

    // Back first releases a pinned digit, then — on the next press — leaves the game as usual.
    BackHandler(enabled = state.insertDigit != null, onBack = viewModel::exitInsertMode)

    GameFunctionRow(state = state, viewModel = viewModel, onLeave = onLeave)
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
            onCellClick = { cell ->
                // A pinned digit that could not go into the tapped cell is refused out loud: the
                // selection moves there, so the reason is in view, and the phone says no.
                if (!viewModel.tapCell(cell)) haptics.performHapticFeedback(HapticFeedbackType.Reject)
            },
            enabled = state.isPlayable,
            eightOfNineHints = state.eightOfNineHints,
            lastMove = state.lastMove,
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
        onDigitClick = viewModel::pressDigit,
        enabled = state.canPressDigit,
        insertDigit = state.insertDigit,
        pinEnabled = state.canPinDigit,
        onDigitLongClick = viewModel::pinDigit,
        candidateMode = state.candidateMode,
        showRemainingCounts = state.showRemainingCounts,
    )

    GameActionsRow(
        canUndo = state.canUndo,
        // Erasing acts on the selected cell; undo and the candidate toggle do not.
        canErase = state.canEnterDigit,
        canReveal = state.canRevealDigit,
        showReveal = state.showRevealDigit,
        canClearCandidates = state.canClearCandidates,
        candidateMode = state.candidateMode,
        enabled = state.isPlayable,
        onReveal = viewModel::revealDigit,
        onUndo = viewModel::undo,
        onErase = viewModel::erase,
        onToggleCandidates = viewModel::toggleCandidateMode,
        onRequestCandidateClear = viewModel::requestCandidateClear,
    )
}

/**
 * A row of actions above the board.
 *
 * Compact icons with captions rather than full-width buttons because there is no room for those
 * beside a 1:1 board. The row stays separate from the app bar, which only has one line.
 *
 * A Daily gets the ranking badge here instead. Sharing and saving are absent for it on purpose;
 * see [GameUiState.canShareAndSave].
 */
@Composable
private fun GameFunctionRow(state: GameUiState, viewModel: GameViewModel, onLeave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(FUNCTION_ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.gameType == GameType.DAILY) {
            RankedBadge(ranked = state.rankedEligible)
        }

        state.insertDigit?.let { digit ->
            PinnedDigitBanner(digit = digit, onEnd = viewModel::exitInsertMode)
        }

        Spacer(Modifier.weight(1f))

        // Debug builds only. Reaching the end of a Daily is otherwise twenty minutes of typing,
        // which is too much to pay every time the ranking needs testing.
        if (state.debugTools && state.phase == GameUiState.Phase.PLAYING) {
            LabeledGameAction(
                label = stringResource(R.string.game_debug_solve),
                iconRes = R.drawable.ic_bulb_filled,
                onClick = viewModel::autoSolve,
            )
        }

        if (state.canShareAndSave) {
            ShareBoardAction(state.difficulty, viewModel::shareCode)
            LabeledGameAction(
                label = stringResource(R.string.game_save_and_exit),
                iconRes = R.drawable.ic_saved_games,
                onClick = {
                    viewModel.saveAndStop()
                    onLeave()
                },
            )
        }
    }
}

/**
 * Says that a digit is pinned, and is the way out of it.
 *
 * The highlighted key alone would be easy to miss — wishlist item 16 calls it too little — and a
 * tap on the board that writes a digit the player did not expect is the kind of surprise that
 * costs a mistake. So the mode is also named above the board, next to the thing it changes, and
 * the same element ends it: a pin and the digit say what is going on, the cross says what a tap
 * does.
 */
@Composable
private fun PinnedDigitBanner(digit: Int, onEnd: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val endLabel = stringResource(R.string.game_pinned_banner_end, digit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(BADGE_CORNER_DP.dp))
            .background(colours.primaryContainer)
            // clickable merges the row, so a screen reader hears the state once and the action as
            // the click label; the cross itself stays silent rather than saying it twice.
            .clickable(onClickLabel = endLabel, role = Role.Button, onClick = onEnd)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pin),
            contentDescription = null,
            modifier = Modifier.size(BADGE_ICON_DP.dp),
            tint = colours.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.game_pinned_banner, digit),
            style = MaterialTheme.typography.labelMedium,
            color = colours.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            modifier = Modifier.size(BADGE_ICON_DP.dp),
            tint = colours.onPrimaryContainer,
        )
    }
}

/** A compact icon with a stable caption, shared by every action in the function row. */
@Composable
private fun LabeledGameAction(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(FUNCTION_ACTION_WIDTH_DP.dp)
            .fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(FUNCTION_ACTION_ICON_DP.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Says whether this game still counts (project.md section 28).
 *
 * Stated rather than left to be inferred from a missing pause button. The rule is real and one-way,
 * and a player who lost their place in the ranking by answering a message deserves to be told, not
 * to work it out from an interface that quietly grew a control.
 */
@Composable
private fun RankedBadge(ranked: Boolean) {
    val colours = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(BADGE_CORNER_DP.dp))
            .background(if (ranked) colours.secondaryContainer else colours.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ranked),
            contentDescription = null,
            modifier = Modifier.size(BADGE_ICON_DP.dp),
            tint = if (ranked) colours.onSecondaryContainer else colours.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (ranked) R.string.game_ranked else R.string.game_unranked),
            style = MaterialTheme.typography.labelMedium,
            color = if (ranked) colours.onSecondaryContainer else colours.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Time, mistakes, assistance used and pause — the compact record of the current attempt. */
@Composable
private fun GameStatusRow(state: GameUiState, onTogglePause: () -> Unit) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            LabelledValue(
                label = stringResource(R.string.game_time),
                value = formatDuration(context, state.elapsedMillis),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            LabelledValue(
                label = stringResource(R.string.game_mistakes),
                value = stringResource(R.string.game_mistakes_value, state.mistakeCount),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
        }
        if (state.showRevealDigit) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HintCountValue(state.hintCount)
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // Absent, not disabled: a greyed-out pause invites the player to wonder what they did
        // wrong, when the answer is simply that this game counts (project.md section 28).
        if (state.canPause) {
            PauseButton(paused = state.isPaused, onClick = onTogglePause)
        }
        }
    }
}

/** A bulb keeps the fourth status column readable where the translated label would not fit. */
@Composable
private fun HintCountValue(count: Int) {
    val description = stringResource(R.string.game_hints_used, count)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bulb_outline),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Pause and resume in one slot, wide enough for whichever word is longer.
 *
 * The two labels are not the same length — "Pause" against "Resume", "Pauza" against "Wznów" — and
 * a button that resized on the flip dragged the mistake counter beside it sideways. Both words are
 * laid out and only one is painted, so the slot is the width of the wider and never moves. Doing it
 * this way rather than with a fixed width keeps it true in a language nobody has added yet.
 */
@Composable
private fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    val pause = stringResource(R.string.game_pause)
    val resume = stringResource(R.string.game_resume)
    val label = if (paused) resume else pause

    TextButton(onClick = onClick) {
        Box(
            contentAlignment = Alignment.Center,
            // The two ghosts are layout, not content; a screen reader must hear one word.
            modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        ) {
            Text(text = pause, modifier = Modifier.alpha(0f))
            Text(text = resume, modifier = Modifier.alpha(0f))
            Text(text = label)
        }
    }
}

@Composable
private fun LabelledValue(
    label: String,
    value: String,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(horizontalAlignment = horizontalAlignment) {
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
    canErase: Boolean,
    canReveal: Boolean,
    showReveal: Boolean,
    canClearCandidates: Boolean,
    candidateMode: Boolean,
    enabled: Boolean,
    onReveal: () -> Unit,
    onUndo: () -> Unit,
    onErase: () -> Unit,
    onToggleCandidates: () -> Unit,
    onRequestCandidateClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ACTION_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(ACTION_GAP_DP.dp),
    ) {
        // project.md section 18: absent, not disabled, while a Daily still counts publicly.
        if (showReveal) {
            OutlinedButton(
                onClick = onReveal,
                enabled = enabled && canReveal,
                modifier = Modifier.weight(REVEAL_ACTION_WEIGHT).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = ACTION_HORIZONTAL_PADDING_DP.dp),
            ) {
                ActionButtonContent(
                    label = stringResource(R.string.game_reveal_digit),
                    iconRes = R.drawable.ic_bulb_outline,
                )
            }
        }
        OutlinedButton(
            onClick = onUndo,
            enabled = enabled && canUndo,
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ACTION_HORIZONTAL_PADDING_DP.dp),
        ) {
            ActionButtonContent(
                label = stringResource(R.string.game_undo),
                iconRes = R.drawable.ic_undo,
            )
        }
        OutlinedButton(
            onClick = onErase,
            enabled = enabled && canErase,
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ACTION_HORIZONTAL_PADDING_DP.dp),
        ) {
            ActionButtonContent(
                label = stringResource(R.string.game_erase),
                iconRes = R.drawable.ic_eraser,
            )
        }
        CandidateModeButton(
            candidateMode = candidateMode,
            enabled = enabled,
            canClearCandidates = canClearCandidates,
            onToggle = onToggleCandidates,
            onRequestClear = onRequestCandidateClear,
            modifier = Modifier.weight(CANDIDATE_ACTION_WEIGHT).fillMaxSize(),
        )
    }
}

/**
 * Candidate mode on tap, whole-board candidate clear on long press.
 *
 * A dedicated surface is used because Material buttons expose clicks only. Pointer gestures and
 * accessibility actions share the same callbacks, while the marker and fill still communicate
 * candidate mode independently.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateModeButton(
    candidateMode: Boolean,
    enabled: Boolean,
    canClearCandidates: Boolean,
    onToggle: () -> Unit,
    onRequestClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme
    val clearLabel = stringResource(R.string.game_clear_candidates_action)
    val description = stringResource(
        if (candidateMode) R.string.game_candidates_on else R.string.game_candidates_off,
    )
    val containerColour = when {
        !enabled && candidateMode -> colours.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA)
        candidateMode -> colours.secondaryContainer
        else -> Color.Transparent
    }
    val contentColour = when {
        !enabled -> colours.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
        candidateMode -> colours.onSecondaryContainer
        else -> colours.primary
    }
    val border = if (candidateMode) {
        null
    } else {
        BorderStroke(
            width = 1.dp,
            color = if (enabled) colours.outline else colours.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA),
        )
    }

    Surface(
        modifier = modifier
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onToggle,
                onLongClickLabel = clearLabel.takeIf { canClearCandidates },
                onLongClick = onRequestClear.takeIf { canClearCandidates },
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(50),
        color = containerColour,
        contentColor = contentColour,
        border = border,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = ACTION_HORIZONTAL_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            CandidateLabel(candidateMode = candidateMode)
        }
    }
}

/** Horizontal icon-and-label content used by actions tied to the selected cell. */
@Composable
private fun ActionButtonContent(label: String, iconRes: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ACTION_ICON_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ACTION_ICON_DP.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A short stable label plus a shape marker, so state never depends on colour or extra prose. */
@Composable
private fun CandidateLabel(candidateMode: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CANDIDATE_MARKER_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (candidateMode) "✓" else "○",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.game_candidates),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CandidateClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_clear_candidates_title)) },
        text = { Text(stringResource(R.string.game_clear_candidates_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.game_clear_candidates_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.game_clear_candidates_cancel))
            }
        },
    )
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
private const val FUNCTION_ROW_HEIGHT_DP = 56
private const val FUNCTION_ACTION_WIDTH_DP = 64
private const val FUNCTION_ACTION_ICON_DP = 20
private const val BADGE_CORNER_DP = 8
private const val BADGE_ICON_DP = 16
private const val ACTION_HORIZONTAL_PADDING_DP = 4
private const val CANDIDATE_ACTION_WEIGHT = 1.7f
private const val REVEAL_ACTION_WEIGHT = 1.2f
private const val CANDIDATE_MARKER_GAP_DP = 4
private const val ACTION_ICON_DP = 18
private const val ACTION_ICON_GAP_DP = 3
private const val DISABLED_CONTENT_ALPHA = 0.38f
private const val DISABLED_CONTAINER_ALPHA = 0.12f
