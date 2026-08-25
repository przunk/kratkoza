package com.przunk.kratkoza.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.ui.theme.LocalBoardColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * A 3x3 grid turning slowly in space above the menu, its digits fading in and out.
 *
 * Decoration, and it behaves like it: no touch target, and hidden from TalkBack entirely — a screen
 * reader announcing nine drifting digits above the menu would be noise, not information.
 *
 * Everything is derived from a single repeating [phase] rather than driven by a coroutine loop, and
 * that is the important part. Compose's animation clock stops when the window stops being drawn, so
 * a phase-derived animation pauses on its own the moment the app goes to the background. A `while
 * (true) { delay(…) }` would keep ticking there, and this one runs for as long as anyone leaves the
 * app open on the menu.
 *
 * The two rotations use periods that do not divide into each other, so the motion never repeats a
 * recognisable pose and reads as drift rather than as a loop.
 */
@Composable
fun RotatingBoardArt(modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    val density = LocalDensity.current

    // A fixed order per composition, so the digits are unpredictable but never flicker into a new
    // value mid-fade.
    val sequences = remember {
        val random = Random(System.nanoTime())
        List(CELLS) { (1..Sudoku.SIZE).shuffled(random) }
    }

    val transition = rememberInfiniteTransition(label = "board-art")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * WIDTH_FRACTION, maxHeight)
        if (side <= 0.dp) return@BoxWithConstraints
        val cell = side / Sudoku.BLOCK_SIZE

        Column(
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    rotationY = sin(phase * TAU) * YAW_DEGREES
                    rotationX = sin(phase * TAU * PITCH_PERIOD_RATIO + PITCH_OFFSET) * PITCH_DEGREES
                    // Without a camera the rotation is a flat squash; this is what makes it read as
                    // a plate turning in space rather than a shrinking rectangle.
                    cameraDistance = CAMERA_DISTANCE * density.density
                }
                .drawBehind {
                    val step = size.width / Sudoku.BLOCK_SIZE
                    val stroke = GRID_STROKE_DP.dp.toPx()
                    val line = colors.blockLine.copy(alpha = GRID_ALPHA)
                    for (index in 0..Sudoku.BLOCK_SIZE) {
                        val at = (index * step).coerceIn(stroke / 2, size.width - stroke / 2)
                        drawLine(line, Offset(at, 0f), Offset(at, size.height), stroke)
                        drawLine(line, Offset(0f, at), Offset(size.width, at), stroke)
                    }
                }
                .clearAndSetSemantics { },
        ) {
            for (row in 0 until Sudoku.BLOCK_SIZE) {
                Row {
                    for (column in 0 until Sudoku.BLOCK_SIZE) {
                        val index = row * Sudoku.BLOCK_SIZE + column
                        DriftingDigit(
                            sequence = sequences[index],
                            // Spread the cells across the cycle so they never breathe in unison.
                            cellPhase = phase * DIGIT_CYCLES + index * CELL_STAGGER,
                            cellSize = cell,
                        )
                    }
                }
            }
        }
    }
}

/** One cell: a digit swelling into view and away again, taking the next of [sequence] each time. */
@Composable
private fun DriftingDigit(sequence: List<Int>, cellPhase: Float, cellSize: Dp) {
    val colors = LocalBoardColors.current
    val turn = cellPhase.toInt()
    val within = cellPhase - turn

    // A bell rather than a triangle: the digit is absent for most of its turn and is only briefly
    // legible, which is what keeps nine of them from adding up to a busy screen.
    val visibility = (1f - abs(within * 2f - 1f)).let { it * it }

    Box(
        modifier = Modifier.size(cellSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = sequence[turn % sequence.size].toString(),
            style = TextStyle(
                fontSize = with(LocalDensity.current) { (cellSize * DIGIT_HEIGHT_FRACTION).toSp() },
                fontWeight = FontWeight.Light,
                color = colors.enteredDigit,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.graphicsLayer { alpha = visibility * DIGIT_MAX_ALPHA },
        )
    }
}

private const val CELLS = Sudoku.BLOCK_SIZE * Sudoku.BLOCK_SIZE
private const val TAU = (2 * PI).toFloat()

/** One full turn of the slower rotation. Long enough that the motion is felt, not watched. */
private const val CYCLE_MILLIS = 42_000

private const val YAW_DEGREES = 16f
private const val PITCH_DEGREES = 9f

/** Not a whole number, so the two rotations never come back into step. */
private const val PITCH_PERIOD_RATIO = 0.63f
private const val PITCH_OFFSET = 1.1f
private const val CAMERA_DISTANCE = 14f

private const val WIDTH_FRACTION = 0.62f
private const val GRID_STROKE_DP = 1f
private const val GRID_ALPHA = 0.16f

/** How many digits each cell shows per full cycle. */
private const val DIGIT_CYCLES = 7f
private const val CELL_STAGGER = 0.37f
private const val DIGIT_HEIGHT_FRACTION = 0.42f
private const val DIGIT_MAX_ALPHA = 0.20f
