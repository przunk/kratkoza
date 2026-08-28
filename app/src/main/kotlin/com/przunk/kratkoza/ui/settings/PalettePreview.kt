package com.przunk.kratkoza.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.przunk.kratkoza.ui.theme.BoardColors

/**
 * A 3x3 sample of what a palette does to the board.
 *
 * Shows the things a name cannot: the three highlight tiers, and a given digit beside one the
 * player entered. A row of plain colour swatches would say which hue a palette is and nothing about
 * whether its board is readable, which is the only question worth asking here.
 *
 * Drawn from the same [BoardColors] the board itself uses, so it cannot drift out of agreement with
 * the real thing — including the high-contrast variant, which the caller passes in already applied.
 */
@Composable
fun PalettePreview(
    colors: BoardColors,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = PREVIEW_SIZE_DP.dp,
) {
    val cell = size / CELLS_PER_SIDE

    Column(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(CORNER_DP.dp))
            .background(colors.surface)
            .drawWithContent {
                drawContent()
                // Over the cells, for the same reason the board draws them there: a tinted cell
                // paints its own background and would otherwise erase the lines crossing it.
                val step = this.size.width / CELLS_PER_SIDE
                val stroke = LINE_DP.dp.toPx()
                for (index in 0..CELLS_PER_SIDE) {
                    val at = (index * step).coerceIn(stroke / 2, this.size.width - stroke / 2)
                    drawLine(colors.gridLine, Offset(at, 0f), Offset(at, this.size.height), stroke)
                    drawLine(colors.gridLine, Offset(0f, at), Offset(this.size.width, at), stroke)
                }
            }
            // One description for the whole thing; nine tiny cells announced separately would be
            // noise, and the palette's name is what the player is choosing between.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        for (row in 0 until CELLS_PER_SIDE) {
            Row(modifier = Modifier.fillMaxSize()) {
                for (column in 0 until CELLS_PER_SIDE) {
                    PreviewCell(
                        sample = SAMPLES[row][column],
                        colors = colors,
                        cellSize = cell,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCell(sample: Sample, colors: BoardColors, cellSize: Dp) {
    val background = when (sample) {
        Sample.SELECTED -> colors.selectedCell
        Sample.SAME_DIGIT -> colors.sameDigitCell
        Sample.PEER -> colors.peerCell
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier.size(cellSize).background(background),
        contentAlignment = Alignment.Center,
    ) {
        val digit = when (sample) {
            Sample.GIVEN -> "4"
            Sample.ENTERED, Sample.SAME_DIGIT -> "7"
            else -> null
        }
        if (digit != null) {
            Text(
                text = digit,
                style = TextStyle(
                    fontSize = with(LocalDensity.current) { (cellSize * DIGIT_FRACTION).toSp() },
                    fontWeight = if (sample == Sample.GIVEN) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (sample == Sample.GIVEN) colors.givenDigit else colors.enteredDigit,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

private enum class Sample { PLAIN, PEER, SAME_DIGIT, SELECTED, GIVEN, ENTERED }

/**
 * The sample board. Laid out so the selected cell shares its row and column with peers, exactly as
 * a real selection would, rather than scattering the tiers at random.
 */
private val SAMPLES = listOf(
    listOf(Sample.GIVEN, Sample.PEER, Sample.PLAIN),
    listOf(Sample.PEER, Sample.SELECTED, Sample.PEER),
    listOf(Sample.PLAIN, Sample.ENTERED, Sample.SAME_DIGIT),
)

private const val CELLS_PER_SIDE = 3
private const val PREVIEW_SIZE_DP = 60
private const val CORNER_DP = 6
private const val LINE_DP = 1f
private const val DIGIT_FRACTION = 0.6f
