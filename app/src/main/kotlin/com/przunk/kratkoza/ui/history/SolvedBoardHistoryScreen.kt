package com.przunk.kratkoza.ui.history

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.game.SolvedBoard
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.common.labelRes
import com.przunk.kratkoza.ui.common.shareBoard
import com.przunk.kratkoza.ui.theme.LocalBoardColors
import com.przunk.kratkoza.util.formatDateTime
import com.przunk.kratkoza.util.formatDuration

@Composable
fun SolvedBoardHistoryScreen(
    viewModel: SolvedBoardHistoryViewModel,
    onOpenDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    KratkozaScaffold(
        title = stringResource(R.string.history_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        if (state.boards.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@KratkozaScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.boards, key = SolvedBoard::id) { board ->
                SolvedBoardRow(
                    board = board,
                    onOpen = { onOpenDetail(board.id) },
                    onShare = {
                        shareBoard(context, board.difficulty.labelRes(), viewModel.shareCodeFor(board))
                    },
                )
            }
        }
    }
}

@Composable
private fun SolvedBoardRow(
    board: SolvedBoard,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SolvedBoardPreview(board.givens, Modifier.size(92.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(board.difficulty.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatDateTime(context, board.completedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.history_result,
                        formatDuration(context, board.elapsedMillis),
                        pluralStringResource(
                            R.plurals.mistake_count,
                            board.mistakeCount,
                            board.mistakeCount,
                        ),
                        pluralStringResource(
                            R.plurals.hint_count,
                            board.hintCount,
                            board.hintCount,
                        ),
                        board.xpEarned,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.history_solved_count,
                        board.completionCount,
                        board.completionCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.history_share),
                )
            }
        }
    }
}

@Composable
fun SolvedBoardDetailScreen(
    viewModel: SolvedBoardDetailViewModel,
    onOpenGame: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openedPuzzleId.collect(onOpenGame)
    }

    KratkozaScaffold(
        title = stringResource(R.string.history_detail_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        val board = state.board
        if (board == null) {
            if (!state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.history_missing))
                }
            }
            return@KratkozaScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SolvedBoardPreview(
                    givens = board.givens,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
            item {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(board.difficulty.labelRes()),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        DetailLine(
                            stringResource(R.string.history_completed_at),
                            formatDateTime(context, board.completedAt),
                        )
                        DetailLine(
                            stringResource(R.string.history_time),
                            formatDuration(context, board.elapsedMillis),
                        )
                        DetailLine(
                            stringResource(R.string.game_mistakes),
                            board.mistakeCount.toString(),
                        )
                        DetailLine(
                            stringResource(R.string.history_hints),
                            board.hintCount.toString(),
                        )
                        DetailLine(stringResource(R.string.history_xp), board.xpEarned.toString())
                        DetailLine(
                            stringResource(R.string.history_completions),
                            pluralStringResource(
                                R.plurals.history_solved_count,
                                board.completionCount,
                                board.completionCount,
                            ),
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            shareBoard(
                                context,
                                board.difficulty.labelRes(),
                                viewModel.shareCodeFor(board),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history_share))
                    }
                    Button(
                        onClick = viewModel::play,
                        enabled = !state.isOpening,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (board.inProgress) R.drawable.ic_play else R.drawable.ic_replay,
                            ),
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (board.inProgress) {
                                    R.string.history_continue
                                } else {
                                    R.string.history_solve_again
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** Read-only rendering of the givens, shared by the archive list and its detail screen. */
@Composable
private fun SolvedBoardPreview(givens: SudokuBoard, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    val description = stringResource(R.string.history_preview)
    val paint = remember(colors.givenDigit) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.givenDigit.toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(
        modifier = modifier
            .background(colors.surface)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val cell = size.width / Sudoku.SIZE
        val thin = 1.dp.toPx()
        val thick = 2.dp.toPx()

        for (line in 0..Sudoku.SIZE) {
            val position = line * cell
            val block = line % 3 == 0
            val color = if (block) colors.blockLine else colors.gridLine
            val stroke = if (block) thick else thin
            drawLine(color, Offset(position, 0f), Offset(position, size.height), stroke)
            drawLine(color, Offset(0f, position), Offset(size.width, position), stroke)
        }

        paint.textSize = cell * 0.58f
        val baselineOffset = -(paint.ascent() + paint.descent()) / 2f
        drawIntoCanvas { canvas ->
            for (index in 0 until Sudoku.CELL_COUNT) {
                val digit = givens[index]
                if (digit == Sudoku.EMPTY) continue
                val x = (Sudoku.columnOf(index) + 0.5f) * cell
                val y = (Sudoku.rowOf(index) + 0.5f) * cell + baselineOffset
                canvas.nativeCanvas.drawText(digit.toString(), x, y, paint)
            }
        }
    }
}
