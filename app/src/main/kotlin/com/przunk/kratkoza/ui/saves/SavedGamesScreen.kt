package com.przunk.kratkoza.ui.saves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.game.SavedGameSummary
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.theme.LocalBoardColors
import com.przunk.kratkoza.util.formatDateTime
import com.przunk.kratkoza.util.formatDuration
import java.text.NumberFormat

@Composable
fun SavedGamesScreen(
    viewModel: SavedGamesViewModel,
    onOpenGame: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Which row, if any, is showing its confirmation. Held for the list rather than per row, so
    // arming a second one disarms the first: two rows both offering to delete would be a trap.
    var armedPuzzleId by remember { mutableStateOf<String?>(null) }

    // Deleting a half-solved game cannot be undone, and the risk here is a mis-tap rather than a
    // misunderstanding — so the guard is a second, deliberate tap rather than a dialog. It steps
    // back on its own, because a button left sitting in its armed state is the mis-tap it was meant
    // to prevent.
    LaunchedEffect(armedPuzzleId) {
        if (armedPuzzleId != null) {
            delay(DELETE_ARMED_MILLIS)
            armedPuzzleId = null
        }
    }

    KratkozaScaffold(
        title = stringResource(R.string.saved_games_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        if (state.saves.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.saved_games_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@KratkozaScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.saves, key = { it.puzzleId }) { save ->
                SavedGameRow(
                    save = save,
                    armed = armedPuzzleId == save.puzzleId,
                    onOpen = { onOpenGame(save.puzzleId) },
                    onArmDelete = { armedPuzzleId = save.puzzleId },
                    onConfirmDelete = {
                        armedPuzzleId = null
                        viewModel.delete(save.puzzleId)
                    },
                )
            }
        }
    }
}

@Composable
private fun SavedGameRow(
    save: SavedGameSummary,
    armed: Boolean,
    onOpen: () -> Unit,
    onArmDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val context = LocalContext.current
    // Locale-aware, like every other number the app shows (project.md section 4).
    val percentFormat = remember { NumberFormat.getPercentInstance() }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoardPreview(save)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.saved_games_entry,
                        stringResource(save.difficulty.labelRes()),
                        formatDuration(context, save.elapsedMillis),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatDateTime(context, save.lastPlayedAt),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(
                        R.string.saved_games_progress,
                        percentFormat.format(save.completionFraction),
                        pluralStringResource(
                            R.plurals.mistake_count,
                            save.mistakeCount,
                            save.mistakeCount,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            DeleteControl(armed = armed, onArm = onArmDelete, onConfirm = onConfirmDelete)
        }
    }
}

/**
 * A thumbnail of the board (project.md section 22).
 *
 * Drawn as dots rather than digits: at this size a digit is illegible anyway, and the pattern of
 * what is filled in is what lets the player recognise which game this is.
 *
 * The player's own entries are told from the givens by contrast, not by size: the givens sit a step
 * quieter than the digit colour used on the board, and the entries keep the full accent. Making the
 * entries bigger as well was tried and read as clutter — at nine dots across, weight of colour
 * carries it and weight of size does not.
 */
@Composable
private fun BoardPreview(save: SavedGameSummary) {
    val colors = LocalBoardColors.current
    val description = stringResource(R.string.saved_games_preview)

    Box(
        modifier = Modifier
            .size(PREVIEW_SIZE_DP.dp)
            .background(colors.surface)
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                val cell = size.width / Sudoku.SIZE

                for (index in 0 until Sudoku.CELL_COUNT) {
                    val value = save.board[index]
                    if (value == Sudoku.EMPTY) continue
                    val entered = save.givens.isEmpty(index)
                    val centre = Offset(
                        x = (Sudoku.columnOf(index) + 0.5f) * cell,
                        y = (Sudoku.rowOf(index) + 0.5f) * cell,
                    )
                    drawCircle(
                        color = if (entered) colors.enteredDigit else colors.candidate,
                        radius = cell * DOT_RADIUS_FRACTION,
                        center = centre,
                    )
                }

                for (line in 1 until Sudoku.BLOCK_SIZE) {
                    val offset = line * Sudoku.BLOCK_SIZE * cell
                    drawRect(
                        color = colors.blockLine,
                        topLeft = Offset(offset, 0f),
                        size = Size(BLOCK_LINE_PX, size.height),
                    )
                    drawRect(
                        color = colors.blockLine,
                        topLeft = Offset(0f, offset),
                        size = Size(size.width, BLOCK_LINE_PX),
                    )
                }
            },
    )
}

/**
 * A bin that turns into the word DELETE when tapped, and deletes on the second tap.
 *
 * The two states are separate controls to a screen reader as well as to the eye: the description
 * changes from "delete" to "confirm deleting this game", so TalkBack does not announce the same
 * button twice and quietly destroy a game on the second tap.
 */
@Composable
private fun DeleteControl(armed: Boolean, onArm: () -> Unit, onConfirm: () -> Unit) {
    if (armed) {
        val armedDescription = stringResource(R.string.saved_games_delete_armed)
        TextButton(
            onClick = onConfirm,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.semantics {
                contentDescription = armedDescription
            },
        ) {
            Text(
                text = stringResource(R.string.saved_games_delete_confirm),
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        IconButton(onClick = onArm) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.saved_games_delete),
            )
        }
    }
}

/** How long the confirmation waits before stepping back to the bin. */
private const val DELETE_ARMED_MILLIS = 3_000L

private const val PREVIEW_SIZE_DP = 64
private const val DOT_RADIUS_FRACTION = 0.22f
private const val BLOCK_LINE_PX = 1f
