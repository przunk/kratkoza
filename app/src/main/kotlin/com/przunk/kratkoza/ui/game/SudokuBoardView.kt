package com.przunk.kratkoza.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.game.CandidateGrid
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.ui.theme.BoardColors
import com.przunk.kratkoza.ui.theme.LocalBoardColors

/**
 * The sudoku grid — the most important thing on the screen (project.md section 37).
 *
 * Laid out as nine rows of nine cells rather than drawn on a canvas, so every cell is a real touch
 * target with its own semantics for TalkBack (project.md section 8). The grid lines are painted in
 * one pass behind the cells, which keeps the 3x3 block borders continuous instead of assembling them
 * from 81 individual borders that never quite line up.
 *
 * Text is sized from the cell, not from the system font scale. project.md section 8 asks for a
 * larger system font to be honoured "where it does not wreck the board's geometry" — inside a fixed
 * 1/9th of the width, it would.
 */
@Composable
fun SudokuBoardView(
    board: SudokuBoard,
    givens: SudokuBoard,
    candidates: CandidateGrid,
    incorrectCells: Set<Int>,
    selectedCell: Int?,
    peerCells: Set<Int>,
    sameDigitCells: Set<Int>,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalBoardColors.current
    val boardDescription = stringResource(R.string.board_description)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val cellSize = maxWidth / Sudoku.SIZE

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                // Over the cells, not under them. A highlighted cell fills its own background,
                // and a line drawn underneath would be erased by it — which is why selecting a cell
                // used to punch a hole in its block's borders.
                .drawWithContent {
                    drawContent()
                    drawGridLines(colors)
                }
                .semantics { contentDescription = boardDescription },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until Sudoku.SIZE) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until Sudoku.SIZE) {
                            val index = Sudoku.index(row, column)
                            SudokuCell(
                                index = index,
                                value = board[index],
                                isGiven = !givens.isEmpty(index),
                                isIncorrect = index in incorrectCells,
                                candidates = candidates[index],
                                background = backgroundOf(
                                    index = index,
                                    selectedCell = selectedCell,
                                    peerCells = peerCells,
                                    sameDigitCells = sameDigitCells,
                                    incorrectCells = incorrectCells,
                                    colors = colors,
                                ),
                                isSelected = index == selectedCell,
                                cellSize = cellSize,
                                colors = colors,
                                enabled = enabled,
                                onClick = onCellClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Which highlight a cell gets.
 *
 * Ordered strongest first. A cell is only ever one thing: an error outranks the selection, because
 * a wrong digit the player is looking at must stay legible as wrong; the selection outranks the
 * matching-digit tint; and that outranks the row/column/block tint. project.md section 10 requires
 * exactly this — matching digits must never bury the selection, the peers or an error.
 */
private fun backgroundOf(
    index: Int,
    selectedCell: Int?,
    peerCells: Set<Int>,
    sameDigitCells: Set<Int>,
    incorrectCells: Set<Int>,
    colors: BoardColors,
): Color = when {
    index in incorrectCells -> colors.errorCell
    index == selectedCell -> colors.selectedCell
    index in sameDigitCells -> colors.sameDigitCell
    index in peerCells -> colors.peerCell
    else -> Color.Transparent
}

@Composable
private fun SudokuCell(
    index: Int,
    value: Int,
    isGiven: Boolean,
    isIncorrect: Boolean,
    candidates: Set<Int>,
    background: Color,
    isSelected: Boolean,
    cellSize: Dp,
    colors: BoardColors,
    enabled: Boolean,
    onClick: (Int) -> Unit,
) {
    val description = cellDescription(index, value, isGiven, isIncorrect, candidates, isSelected)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(cellSize)
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(index) },
            )
            // One description for the whole cell: TalkBack should read "row 3, column 5, 7" once,
            // not announce a digit with no idea where it sits.
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            value != Sudoku.EMPTY -> DigitText(
                value = value,
                isGiven = isGiven,
                isIncorrect = isIncorrect,
                cellSize = cellSize,
                colors = colors,
            )

            candidates.isNotEmpty() -> CandidateGridText(
                candidates = candidates,
                cellSize = cellSize,
                colors = colors,
            )
        }
    }
}

@Composable
private fun DigitText(
    value: Int,
    isGiven: Boolean,
    isIncorrect: Boolean,
    cellSize: Dp,
    colors: BoardColors,
) {
    Text(
        text = value.toString(),
        style = TextStyle(
            fontSize = with(LocalDensity.current) { (cellSize * DIGIT_HEIGHT_FRACTION).toSp() },
            // The player's digits are the heavy ones, not the givens. Weight carries the
            // distinction alongside colour, so it does not rest on hue (project.md section 8).
            fontWeight = if (isGiven) FontWeight.Normal else FontWeight.SemiBold,
            color = when {
                isIncorrect -> colors.errorDigit
                isGiven -> colors.givenDigit
                else -> colors.enteredDigit
            },
            textAlign = TextAlign.Center,
        ),
        modifier = if (isIncorrect) {
            // The second, non-colour cue for a mistake: a rule under the digit.
            Modifier.drawBehind {
                val inset = size.width * ERROR_RULE_INSET
                val y = size.height * ERROR_RULE_BASELINE
                drawLine(
                    color = colors.errorDigit,
                    start = Offset(inset, y),
                    end = Offset(size.width - inset, y),
                    strokeWidth = size.height * ERROR_RULE_THICKNESS,
                )
            }
        } else {
            Modifier
        },
    )
}

/** Pencil marks as a 3x3 mini grid, each digit always in its own place (project.md section 14). */
@Composable
private fun CandidateGridText(
    candidates: Set<Int>,
    cellSize: Dp,
    colors: BoardColors,
) {
    val fontSize = with(LocalDensity.current) { (cellSize * CANDIDATE_HEIGHT_FRACTION).toSp() }

    Column(modifier = Modifier.fillMaxSize()) {
        for (row in 0 until Sudoku.BLOCK_SIZE) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (column in 0 until Sudoku.BLOCK_SIZE) {
                    val digit = row * Sudoku.BLOCK_SIZE + column + 1
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit in candidates) {
                            Text(
                                text = digit.toString(),
                                style = TextStyle(
                                    fontSize = fontSize,
                                    color = colors.candidate,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Paints the grid: a hairline between cells, a heavy line between 3x3 blocks, and a heavy border
 * around the whole board (project.md section 8).
 */
private fun DrawScope.drawGridLines(colors: BoardColors) {
    val cell = size.width / Sudoku.SIZE
    val thin = THIN_LINE_DP.dp.toPx()
    val thick = THICK_LINE_DP.dp.toPx()

    for (line in 0..Sudoku.SIZE) {
        val isBlockEdge = line % Sudoku.BLOCK_SIZE == 0
        val width = if (isBlockEdge) thick else thin
        val color = if (isBlockEdge) colors.blockLine else colors.gridLine
        // Half a stroke in from each edge, so the outer border sits inside the board rather than
        // being clipped in half by it.
        val offset = (line * cell).coerceIn(width / 2, size.width - width / 2)

        drawLine(color, Offset(offset, 0f), Offset(offset, size.height), width)
        drawLine(color, Offset(0f, offset), Offset(size.width, offset), width)
    }
}

@Composable
private fun cellDescription(
    index: Int,
    value: Int,
    isGiven: Boolean,
    isIncorrect: Boolean,
    candidates: Set<Int>,
    isSelected: Boolean,
): String {
    val row = Sudoku.rowOf(index) + 1
    val column = Sudoku.columnOf(index) + 1

    val base = when {
        isIncorrect -> stringResource(R.string.board_cell_incorrect, row, column, value)
        value != Sudoku.EMPTY && isGiven -> stringResource(R.string.board_cell_given, row, column, value)
        value != Sudoku.EMPTY -> stringResource(R.string.board_cell_entered, row, column, value)
        candidates.isNotEmpty() -> stringResource(
            R.string.board_cell_candidates,
            row,
            column,
            candidates.joinToString(", "),
        )
        else -> stringResource(R.string.board_cell_empty, row, column)
    }

    val selectedSuffix = stringResource(R.string.board_cell_selected)
    return if (isSelected) "$base, $selectedSuffix" else base
}

private const val DIGIT_HEIGHT_FRACTION = 0.58f
private const val CANDIDATE_HEIGHT_FRACTION = 0.26f
private const val THIN_LINE_DP = 0.5f
private const val THICK_LINE_DP = 2f
private const val ERROR_RULE_INSET = 0.18f
private const val ERROR_RULE_BASELINE = 0.94f
private const val ERROR_RULE_THICKNESS = 0.05f
