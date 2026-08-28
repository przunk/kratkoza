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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
 * Compose has no child `translateZ` equivalent to CSS preserve-3d. Independent drawing layers
 * therefore establish the scene explicitly: dark ground, rear sparks, opaque board, middle sparks,
 * digits, then foreground sparks. The board and digits share one perspective rotation but differ
 * in scale and translation, which makes their order readable without ambiguous translucent planes.
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
                depth = SPARK_MIN_DEPTH + random.nextFloat() * SPARK_DEPTH_RANGE,
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
    val digitColor = lerp(colors.enteredDigit, Color.White, DIGIT_COLOR_LIFT)
    val gridColor = lerp(colors.blockLine, Color.White, GRID_COLOR_LIFT)

    BoxWithConstraints(
        modifier = modifier
            .background(SCENE_BACKGROUND)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.TopCenter,
    ) {
        val side = minOf(maxWidth * WIDTH_FRACTION, maxHeight, MAX_SIDE_DP.dp)
        if (side <= 0.dp) return@BoxWithConstraints
        val cell = side / Sudoku.BLOCK_SIZE

        SparkField(
            sparks = sparks,
            layer = SparkDepthLayer.BEHIND_BOARD,
            phase = phase,
            tilt = tilt,
            color = digitColor,
            density = density.density,
            modifier = Modifier.fillMaxSize().zIndex(Z_REAR_SPARKS),
        )

        // An opaque board is the visual anchor. It is deliberately drawn rather than promoted to
        // a clipped shadow layer, which avoids the rectangular compositing artefacts seen on-device.
        Box(
            modifier = Modifier
                .size(side)
                .zIndex(Z_BOARD)
                .boardLayer(
                    tilt = tilt,
                    density = density.density,
                    depth = BOARD_DEPTH,
                )
                .drawBehind {
                    drawRect(BOARD_BACKGROUND)
                    val step = size.width / Sudoku.BLOCK_SIZE
                    val stroke = GRID_STROKE_DP.dp.toPx()
                    val line = gridColor.copy(alpha = GRID_ALPHA)
                    for (index in 0..Sudoku.BLOCK_SIZE) {
                        val at = (index * step).coerceIn(stroke / 2, size.width - stroke / 2)
                        drawLine(line, Offset(at, 0f), Offset(at, size.height), stroke)
                        drawLine(line, Offset(0f, at), Offset(size.width, at), stroke)
                    }
                },
        )

        SparkField(
            sparks = sparks,
            layer = SparkDepthLayer.BETWEEN_BOARD_AND_DIGITS,
            phase = phase,
            tilt = tilt,
            color = digitColor,
            density = density.density,
            modifier = Modifier.fillMaxSize().zIndex(Z_MIDDLE_SPARKS),
        )

        Column(
            modifier = Modifier
                .size(side)
                .zIndex(Z_DIGITS)
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
                            color = digitColor,
                        )
                    }
                }
            }
        }

        SparkField(
            sparks = sparks,
            layer = SparkDepthLayer.IN_FRONT_OF_DIGITS,
            phase = phase,
            tilt = tilt,
            color = digitColor,
            density = density.density,
            modifier = Modifier.fillMaxSize().zIndex(Z_FRONT_SPARKS),
        )

        // Input is its own top layer. It has no pixels and consumes no event, so the visual order
        // remains independent from the gesture implementation.
        Box(
            modifier = Modifier
                .size(side)
                .zIndex(Z_INPUT)
                .boardParallaxInput(
                    touchBlend = touchBlend,
                    onTilt = { touchTilt = it },
                ),
        )
    }
}

/**
 * Draws only one depth band. A spark keeps its own depth inside that band, so nearer particles are
 * larger, brighter and travel farther than distant ones instead of moving as one flat wallpaper.
 */
@Composable
private fun SparkField(
    sparks: List<Spark>,
    layer: SparkDepthLayer,
    phase: Float,
    tilt: BoardTilt,
    color: Color,
    density: Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .drawBehind {
                val yaw = (tilt.yaw / MAX_VISUAL_YAW_DEGREES).coerceIn(-1f, 1f)
                val pitch = (tilt.pitch / MAX_VISUAL_PITCH_DEGREES).coerceIn(-1f, 1f)

                for (spark in sparks) {
                    if (sparkDepthLayer(spark.depth) != layer) continue

                    val life = (phase * spark.speed + spark.offset).let { it - it.toInt() }
                    val glow = 1f - abs(life * 2f - 1f)
                    val depthScale = (1f + spark.depth * SPARK_DEPTH_SCALE)
                        .coerceIn(SPARK_MIN_DEPTH_SCALE, SPARK_MAX_DEPTH_SCALE)
                    val depthAlpha = (SPARK_DEPTH_ALPHA_BASE + spark.depth * SPARK_DEPTH_ALPHA_STEP)
                        .coerceIn(SPARK_MIN_DEPTH_ALPHA, SPARK_MAX_DEPTH_ALPHA)
                    val travel = spark.depth * SPARK_PARALLAX_TRAVEL_DP * density

                    drawCircle(
                        color = color,
                        radius = spark.radius * size.minDimension *
                            (1f - life * SPARK_SHRINK) * depthScale,
                        center = Offset(
                            x = (spark.x + spark.drift * life * SPARK_SWAY) * size.width -
                                yaw * travel,
                            y = (spark.y - life * spark.rise) * size.height - pitch * travel,
                        ),
                        alpha = glow * glow * SPARK_MAX_ALPHA * depthAlpha,
                    )
                }
            }
            .clearAndSetSemantics { },
    )
}

/** One cell: a digit swelling into view and away again, taking the next of [sequence] each time. */
@Composable
private fun DriftingDigit(sequence: List<Int>, cellPhase: Float, cellSize: Dp, color: Color) {
    val turn = cellPhase.toInt()
    val within = cellPhase - turn

    // A bell still changes the numeral, but the floor keeps all nine visibly in front of the board.
    val visibility = (1f - abs(within * 2f - 1f)).let { it * it }
    val alpha = DIGIT_MIN_ALPHA + visibility * (DIGIT_MAX_ALPHA - DIGIT_MIN_ALPHA)

    Box(
        modifier = Modifier.size(cellSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = sequence[turn % sequence.size].toString(),
            style = TextStyle(
                fontSize = with(LocalDensity.current) { (cellSize * DIGIT_HEIGHT_FRACTION).toSp() },
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = alpha),
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = DIGIT_SHADOW_ALPHA),
                    offset = Offset(0f, DIGIT_SHADOW_OFFSET_PX),
                    blurRadius = DIGIT_SHADOW_BLUR_PX,
                ),
            ),
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

/** Explicit draw band for a particle relative to the two content planes. */
internal enum class SparkDepthLayer {
    BEHIND_BOARD,
    BETWEEN_BOARD_AND_DIGITS,
    IN_FRONT_OF_DIGITS,
}

/** Converts continuous particle depth into the scene's stable drawing order. */
internal fun sparkDepthLayer(depth: Float): SparkDepthLayer = when {
    depth < BOARD_DEPTH -> SparkDepthLayer.BEHIND_BOARD
    depth < DIGIT_DEPTH -> SparkDepthLayer.BETWEEN_BOARD_AND_DIGITS
    else -> SparkDepthLayer.IN_FRONT_OF_DIGITS
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
private const val GRID_STROKE_DP = 1f
private const val GRID_ALPHA = 0.46f

private val SCENE_BACKGROUND = Color(0xFF090E12)
private val BOARD_BACKGROUND = Color(0xFF1C2A34)
private const val DIGIT_COLOR_LIFT = 0.62f
private const val GRID_COLOR_LIFT = 0.48f

private const val BOARD_DEPTH = 0f
private const val DIGIT_DEPTH = 1f
private const val DEPTH_SCALE = 0.025f
private const val MAX_DEPTH_TRAVEL_DP = 6f

private const val Z_REAR_SPARKS = 1f
private const val Z_BOARD = 2f
private const val Z_MIDDLE_SPARKS = 3f
private const val Z_DIGITS = 4f
private const val Z_FRONT_SPARKS = 5f
private const val Z_INPUT = 6f

/** How many digits each cell shows per full cycle. */
private const val DIGIT_CYCLES = 7f
private const val CELL_STAGGER = 0.37f
private const val DIGIT_HEIGHT_FRACTION = 0.42f
private const val DIGIT_MIN_ALPHA = 0.26f
private const val DIGIT_MAX_ALPHA = 0.94f
private const val DIGIT_SHADOW_ALPHA = 0.48f
private const val DIGIT_SHADOW_OFFSET_PX = 2f
private const val DIGIT_SHADOW_BLUR_PX = 4f

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
    val depth: Float,
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
private const val SPARK_MAX_ALPHA = 0.52f
private const val SPARK_MIN_DEPTH = -1.1f
private const val SPARK_DEPTH_RANGE = 2.55f
private const val SPARK_DEPTH_SCALE = 0.20f
private const val SPARK_MIN_DEPTH_SCALE = 0.68f
private const val SPARK_MAX_DEPTH_SCALE = 1.30f
private const val SPARK_DEPTH_ALPHA_BASE = 0.78f
private const val SPARK_DEPTH_ALPHA_STEP = 0.16f
private const val SPARK_MIN_DEPTH_ALPHA = 0.52f
private const val SPARK_MAX_DEPTH_ALPHA = 1f
private const val SPARK_PARALLAX_TRAVEL_DP = 8f
