package com.przunk.kratkoza.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A layered 3x3 grid drifting above the menu, with digits floating in front of its plate.
 *
 * A finger over the artwork temporarily takes over the tilt. Releasing it blends back to the idle
 * motion instead of snapping to a fixed pose, so the interaction feels like touching an object
 * that was already suspended in space. The gesture changes no application state and deliberately
 * consumes nothing; the artwork remains decoration and stays hidden from TalkBack.
 *
 * Compose has no child `translateZ` equivalent to CSS preserve-3d. Separate layers therefore share
 * one perspective rotation while their scale and translation differ by depth. At this size the
 * approximation reads as real separation without a 3D engine or an offscreen blur.
 *
 * Everything persistent is derived from a single repeating [phase]. Compose's animation clock
 * stops when the window is not drawn, so the idle movement and sparks pause with the screen rather
 * than running a coroutine in the background.
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

    val sparks = remember {
        val random = Random(System.nanoTime() + 1)
        List(SPARK_COUNT) {
            Spark(
                x = random.nextFloat(),
                y = random.nextFloat(),
                drift = random.nextFloat() * 2f - 1f,
                rise = SPARK_MIN_RISE + random.nextFloat() * SPARK_RISE_RANGE,
                radius = SPARK_MIN_RADIUS + random.nextFloat() * SPARK_RADIUS_RANGE,
                speed = SPARK_MIN_SPEED + random.nextFloat() * SPARK_SPEED_RANGE,
                offset = random.nextFloat(),
            )
        }
    }

    val touchBlend = remember { Animatable(0f) }
    var touchTilt by remember { mutableStateOf(BoardTilt.Zero) }
    val idleTilt = BoardTilt(
        pitch = sin(phase * TAU * PITCH_PERIOD_RATIO + PITCH_OFFSET) * IDLE_PITCH_DEGREES,
        yaw = sin(phase * TAU) * IDLE_YAW_DEGREES,
    )
    val tilt = blendBoardTilt(idleTilt, touchTilt, touchBlend.value)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val side = minOf(maxWidth * WIDTH_FRACTION, maxHeight, MAX_SIDE_DP.dp)
        if (side <= 0.dp) return@BoxWithConstraints
        val cell = side / Sudoku.BLOCK_SIZE

        // Behind the plate, and drawn across the whole area rather than the square, so the sparks
        // read as the room the board is turning in rather than as part of the board.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val parallax = parallaxOffset(tilt, BACKGROUND_DEPTH, density.density)
                    translationX = parallax.x
                    translationY = parallax.y
                }
                .drawBehind {
                    val haloCenter = Offset(size.width / 2f, side.toPx() / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.enteredDigit.copy(alpha = HALO_CENTER_ALPHA),
                                Color.Transparent,
                            ),
                            center = haloCenter,
                            radius = side.toPx() * HALO_RADIUS_FRACTION,
                        ),
                        radius = side.toPx() * HALO_RADIUS_FRACTION,
                        center = haloCenter,
                    )
                    for (spark in sparks) {
                        val life = (phase * spark.speed + spark.offset).let { it - it.toInt() }
                        // A bell: born from nothing, gone to nothing, brightest halfway up.
                        val glow = 1f - abs(life * 2f - 1f)
                        drawCircle(
                            color = colors.enteredDigit,
                            radius = spark.radius * size.minDimension * (1f - life * SPARK_SHRINK),
                            center = Offset(
                                x = (spark.x + spark.drift * life * SPARK_SWAY) * size.width,
                                y = (spark.y - life * spark.rise) * size.height,
                            ),
                            alpha = glow * glow * SPARK_MAX_ALPHA,
                        )
                    }
                }
                .clearAndSetSemantics { },
        )

        Box(
            modifier = Modifier
                .size(side)
                .boardParallaxInput(
                    touchBlend = touchBlend,
                    onTilt = { touchTilt = it },
                )
                .clearAndSetSemantics { },
        ) {
            val boardShape = remember { RoundedCornerShape(BOARD_CORNER_DP.dp) }

            // The plate carries the perspective and the shadow. Digits are a separate layer so
            // they can travel farther than the grid and appear to float above it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .boardLayer(
                        tilt = tilt,
                        density = density.density,
                        depth = PLATE_DEPTH,
                        shape = boardShape,
                        shadowElevationDp = BOARD_SHADOW_ELEVATION_DP,
                        shadowColor = colors.blockLine.copy(alpha = BOARD_SHADOW_ALPHA),
                        clipToShape = true,
                    )
                    .drawBehind {
                        val corner = CornerRadius(BOARD_CORNER_DP.dp.toPx())
                        drawRoundRect(
                            color = colors.surface.copy(alpha = BOARD_SURFACE_ALPHA),
                            cornerRadius = corner,
                        )

                        val yawShift = (tilt.yaw / MAX_VISUAL_YAW_DEGREES)
                            .coerceIn(-1f, 1f) * size.width * GLINT_TRAVEL_FRACTION
                        val pitchShift = (tilt.pitch / MAX_VISUAL_PITCH_DEGREES)
                            .coerceIn(-1f, 1f) * size.height * GLINT_TRAVEL_FRACTION
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.enteredDigit.copy(alpha = GLINT_MAX_ALPHA),
                                    Color.Transparent,
                                ),
                                start = Offset(size.width * 0.05f + yawShift, -pitchShift),
                                end = Offset(size.width * 0.95f + yawShift, size.height - pitchShift),
                            ),
                            cornerRadius = corner,
                        )

                        val step = size.width / Sudoku.BLOCK_SIZE
                        val stroke = GRID_STROKE_DP.dp.toPx()
                        val line = colors.blockLine.copy(alpha = GRID_ALPHA)
                        for (index in 0..Sudoku.BLOCK_SIZE) {
                            val at = (index * step).coerceIn(stroke / 2, size.width - stroke / 2)
                            drawLine(line, Offset(at, 0f), Offset(at, size.height), stroke)
                            drawLine(line, Offset(0f, at), Offset(size.width, at), stroke)
                        }
                    },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .boardLayer(
                        tilt = tilt,
                        density = density.density,
                        depth = DIGIT_DEPTH,
                    ),
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

/**
 * Tracks one finger without consuming it. The artwork reacts immediately, while release animates
 * only the blend weight; keeping the last pointer pose avoids two competing return animations.
 */
private fun Modifier.boardParallaxInput(
    touchBlend: Animatable<Float, AnimationVector1D>,
    onTilt: (BoardTilt) -> Unit,
): Modifier = pointerInput(touchBlend) {
    coroutineScope {
        var blendJob: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            blendJob?.cancel()
            onTilt(boardTiltForPointer(down.position.x, down.position.y, size.width, size.height))
            blendJob = launch { touchBlend.snapTo(1f) }

            var tracked = down
            while (tracked.pressed) {
                val event = awaitPointerEvent()
                tracked = event.changes.firstOrNull { it.id == down.id } ?: break
                onTilt(
                    boardTiltForPointer(
                        tracked.position.x,
                        tracked.position.y,
                        size.width,
                        size.height,
                    ),
                )
            }

            blendJob?.cancel()
            blendJob = launch {
                touchBlend.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
        }
    }
}

/** Applies the shared perspective plus the small offset that sells distance between flat layers. */
private fun Modifier.boardLayer(
    tilt: BoardTilt,
    density: Float,
    depth: Float,
    shape: Shape = RectangleShape,
    shadowElevationDp: Float = 0f,
    shadowColor: Color = Color.Transparent,
    clipToShape: Boolean = false,
): Modifier = graphicsLayer {
    val parallax = parallaxOffset(tilt, depth, density)
    val scale = 1f + depth * DEPTH_SCALE

    rotationX = tilt.pitch
    rotationY = tilt.yaw
    cameraDistance = CAMERA_DISTANCE * density
    translationX = parallax.x
    translationY = parallax.y
    scaleX = scale
    scaleY = scale
    this.shape = shape
    clip = clipToShape
    shadowElevation = shadowElevationDp * density
    ambientShadowColor = shadowColor
    spotShadowColor = shadowColor
}

private fun parallaxOffset(tilt: BoardTilt, depth: Float, density: Float): Offset {
    val yaw = (tilt.yaw / MAX_VISUAL_YAW_DEGREES).coerceIn(-1f, 1f)
    val pitch = (tilt.pitch / MAX_VISUAL_PITCH_DEGREES).coerceIn(-1f, 1f)
    val travel = depth * MAX_DEPTH_TRAVEL_DP * density
    return Offset(x = -yaw * travel, y = -pitch * travel)
}

/** Perspective angles in degrees. Kept free of Compose state so input mapping is unit-testable. */
internal data class BoardTilt(val pitch: Float, val yaw: Float) {
    companion object {
        val Zero = BoardTilt(pitch = 0f, yaw = 0f)
    }
}

/** Maps the local pointer position to the same bounded tilt regardless of the artwork's size. */
internal fun boardTiltForPointer(x: Float, y: Float, width: Int, height: Int): BoardTilt {
    if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return BoardTilt.Zero
    val horizontal = ((width / 2f - x) / (width / 2f)).coerceIn(-1f, 1f)
    val vertical = ((height / 2f - y) / (height / 2f)).coerceIn(-1f, 1f)
    return BoardTilt(
        pitch = vertical * TOUCH_PITCH_DEGREES,
        yaw = horizontal * TOUCH_YAW_DEGREES,
    )
}

/** Cross-fades back to the live idle pose, rather than returning to a stale angle captured on up. */
internal fun blendBoardTilt(idle: BoardTilt, touch: BoardTilt, touchWeight: Float): BoardTilt {
    val weight = touchWeight.coerceIn(0f, 1f)
    return BoardTilt(
        pitch = idle.pitch + (touch.pitch - idle.pitch) * weight,
        yaw = idle.yaw + (touch.yaw - idle.yaw) * weight,
    )
}

private const val CELLS = Sudoku.BLOCK_SIZE * Sudoku.BLOCK_SIZE
private const val TAU = (2 * PI).toFloat()

/** One full turn of the slower rotation. Long enough that the motion is felt, not watched. */
private const val CYCLE_MILLIS = 42_000

private const val IDLE_YAW_DEGREES = 16f
private const val IDLE_PITCH_DEGREES = 9f
internal const val TOUCH_YAW_DEGREES = 14f
internal const val TOUCH_PITCH_DEGREES = 10f
private const val MAX_VISUAL_YAW_DEGREES = 16f
private const val MAX_VISUAL_PITCH_DEGREES = 10f

/** Not a whole number, so the two idle rotations never come back into step. */
private const val PITCH_PERIOD_RATIO = 0.63f
private const val PITCH_OFFSET = 1.1f
private const val CAMERA_DISTANCE = 14f

private const val WIDTH_FRACTION = 0.42f

/** Keeps the plate modest on a tablet, where 42% of the width would be a poster. */
private const val MAX_SIDE_DP = 180
private const val BOARD_CORNER_DP = 7f
private const val BOARD_SURFACE_ALPHA = 0.22f
private const val BOARD_SHADOW_ELEVATION_DP = 10f
private const val BOARD_SHADOW_ALPHA = 0.28f
private const val GLINT_MAX_ALPHA = 0.13f
private const val GLINT_TRAVEL_FRACTION = 0.12f
private const val GRID_STROKE_DP = 1f
private const val GRID_ALPHA = 0.34f

private const val PLATE_DEPTH = 0f
private const val DIGIT_DEPTH = 1f
private const val BACKGROUND_DEPTH = -0.45f
private const val DEPTH_SCALE = 0.035f
private const val MAX_DEPTH_TRAVEL_DP = 7f
private const val HALO_RADIUS_FRACTION = 0.72f
private const val HALO_CENTER_ALPHA = 0.15f

/** How many digits each cell shows per full cycle. */
private const val DIGIT_CYCLES = 7f
private const val CELL_STAGGER = 0.37f
private const val DIGIT_HEIGHT_FRACTION = 0.42f
private const val DIGIT_MAX_ALPHA = 0.38f

/**
 * One drifting spark. Everything is a fraction of the drawing area, so the same description works
 * at any size, and everything is fixed at composition — the motion comes from the shared phase, so
 * the sparks stop with the rest of it when the app goes to the background.
 */
private data class Spark(
    val x: Float,
    val y: Float,
    val drift: Float,
    val rise: Float,
    val radius: Float,
    val speed: Float,
    val offset: Float,
)

private const val SPARK_COUNT = 36
private const val SPARK_MIN_RISE = 0.25f
private const val SPARK_RISE_RANGE = 0.45f
private const val SPARK_MIN_RADIUS = 0.004f
private const val SPARK_RADIUS_RANGE = 0.008f
private const val SPARK_MIN_SPEED = 1.5f
private const val SPARK_SPEED_RANGE = 2.5f
private const val SPARK_SWAY = 0.12f
private const val SPARK_SHRINK = 0.35f
private const val SPARK_MAX_ALPHA = 0.42f
