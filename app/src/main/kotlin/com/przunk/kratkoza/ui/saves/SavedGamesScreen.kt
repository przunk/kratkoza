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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.game.SavedGameSummary
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.theme.LocalBoardColors
import com.przunk.kratkoza.util.formatDateTime
import com.przunk.kratkoza.util.formatDuration

@Composable
fun SavedGamesScreen(
    viewModel: SavedGamesViewModel,
    onOpenGame: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    onOpen = { onOpenGame(save.puzzleId) },
                    onDelete = { viewModel.delete(save.puzzleId) },
                )
            }
        }
    }
}

@Composable
private fun SavedGameRow(save: SavedGameSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current

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
                    text = pluralStringResource(
                        R.plurals.mistake_count,
                        save.mistakeCount,
                        save.mistakeCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = onDelete) { Text(stringResource(R.string.saved_games_delete)) }
        }
    }
}

/**
 * A thumbnail of the board (project.md section 22).
 *
 * Drawn as dots rather than digits: at this size a digit is illegible anyway, and the pattern of
 * what is filled in is the thing that lets the player recognise which game this is. The player's
 * own entries are drawn in the entered colour so the thumbnail shows progress, not just the givens.
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
                val radius = cell * DOT_RADIUS_FRACTION

                for (index in 0 until Sudoku.CELL_COUNT) {
                    val value = save.board[index]
                    if (value == Sudoku.EMPTY) continue
                    val centre = Offset(
                        x = (Sudoku.columnOf(index) + 0.5f) * cell,
                        y = (Sudoku.rowOf(index) + 0.5f) * cell,
                    )
                    drawCircle(
                        color = if (save.givens.isEmpty(index)) colors.enteredDigit else colors.givenDigit,
                        radius = radius,
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

private const val PREVIEW_SIZE_DP = 64
private const val DOT_RADIUS_FRACTION = 0.22f
private const val BLOCK_LINE_PX = 1f
