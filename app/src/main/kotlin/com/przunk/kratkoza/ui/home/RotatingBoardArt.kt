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
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.ui.theme.LocalBoardColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
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
 * Every loop is periodic at both ends. Compose's animation clock stops when the window is not
 * drawn, so the wobble and sparks pause with the screen rather than running a background coroutine.
 */
@Composable
fun RotatingBoardArt(
    modifier: Modifier = Modifier,
    /**
     * Chooses the digits and the particles, or `null` to pick one per composition.
     *
     * Nullable rather than defaulted to the clock. A default argument is an expression evaluated on
     * every call, and this composable is called on every animation frame — so `System.nanoTime()`
     * as a default handed it a new seed sixty times a second and regenerated the whole scene with
     * it. Passing null and resolving once is the same convenience without that trap.
     */
    seed: Long? = null,
) {
    val colors = LocalBoardColors.current
    val density = LocalDensity.current

    // Resolved once for the life of the composition, not once per frame.
    val artSeed = remember(seed) { seed ?: System.nanoTime() }

    // A fixed order per composition, so the digits are unpredictable but never flicker into a new
    // value mid-fade.
    val sequences = remember(artSeed) {
        val random = Random(artSeed)
        List(CELLS) { (1..Sudoku.SIZE).shuffled(random) }
    }

    val transition = rememberInfiniteTransition(label = "board-art")
    val motionPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(WOBBLE_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wobble-phase",
    )
    val contentPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CONTENT_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "content-phase",
    )

    val sparks = remember(artSeed) {
        val random = Random(artSeed + 1)
        List(SPARK_COUNT) {
            Spark(
                x = random.nextFloat(),
                startY = SPARK_MIN_START_Y + random.nextFloat() * SPARK_START_Y_RANGE,
                drift = random.nextFloat() * 2f - 1f,
                rise = SPARK_MIN_RISE + random.nextFloat() * SPARK_RISE_RANGE,
                radius = SPARK_MIN_RADIUS + random.nextFloat() * SPARK_RADIUS_RANGE,
                cycles = random.nextInt(SPARK_MIN_CYCLES, SPARK_MAX_CYCLES + 1),
                offset = random.nextFloat(),
                depth = SPARK_MIN_DEPTH + random.nextFloat() * SPARK_DEPTH_RANGE,
            )
        }
    }

    val touchBlend = remember { Animatable(0f) }
    var touchTilt by remember { mutableStateOf(BoardTilt.Zero) }
    val idleTilt = idleBoardTilt(motionPhase)
    val tilt = blendBoardTilt(idleTilt, touchTilt, touchBlend.value)
    val digitColor = lerp(colors.enteredDigit, Color.White, DIGIT_COLOR_LIFT)
    val gridColor = lerp(colors.blockLine, Color.White, GRID_COLOR_LIFT)
    val boardColor = lerp(MaterialTheme.colorScheme.background, Color.White, BOARD_BACKGROUND_LIFT)
    val digitShadowOffset = digitShadowOffsetForTilt(tilt, density.density)

    BoxWithConstraints(
        modifier = modifier
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // Perspective can extend past the untransformed bounds. Reserving room on both axes keeps
        // even the maximum finger-driven tilt comfortably inside the title area.
        val side = minOf(
            maxWidth * WIDTH_FRACTION,
            maxHeight * HEIGHT_SAFE_FRACTION,
            MAX_SIDE_DP.dp,
        )
        if (side <= 0.dp) return@BoxWithConstraints
        val cell = side / Sudoku.BLOCK_SIZE

        SparkField(
            sparks = sparks,
            layer = SparkDepthLayer.BEHIND_BOARD,
            phase = contentPhase,
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
                    drawRect(boardColor)
                    val step = size.width / Sudoku.BLOCK_SIZE
                    val stroke = GRID_STROKE_DP.dp.toPx()
                    val line = gridColor.copy(alpha = GRID_ALPHA)
                    for (index in 0..Sudoku.BLOCK_SIZE) {
                        val at = (index * step).coerceIn(stroke / 2, size.width - stroke / 2)
                        drawLine(line, Offset(at, 0f), Offset(at, size.height), stroke)
                        drawLine(line, Offset(0f, at), Offset(size.width, at), stroke)
                    }
                    drawRect(
                        color = digitColor.copy(alpha = BOARD_EDGE_ALPHA),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = BOARD_EDGE_STROKE_DP.dp.toPx(),
                        ),
                    )
                },
        )

        // The blurred glyphs live on the board plane. The bright glyphs below live at digit depth,
        // so their separation changes naturally during wobble and touch instead of behaving like
        // one flat text layer with a CSS-style attached shadow.
        DigitGrid(
            sequences = sequences,
            phase = contentPhase,
            cell = cell,
            color = Color.Black,
            shadowOffset = digitShadowOffset,
            drawAsShadow = true,
            modifier = Modifier
                .size(side)
                .zIndex(Z_DIGIT_SHADOWS)
                .boardLayer(tilt = tilt, density = density.density, depth = BOARD_DEPTH),
        )

        SparkField(
            sparks = sparks,
            layer = SparkDepthLayer.BETWEEN_BOARD_AND_DIGITS,
            phase = contentPhase,
            tilt = tilt,
            color = digitColor,
            density = density.density,
            modifier = Modifier.fillMaxSize().zIndex(Z_MIDDLE_SPARKS),
        )

        DigitGrid(
            sequences = sequences,
            phase = contentPhase,
            cell = cell,
            color = digitColor,
            shadowOffset = Offset.Zero,
            drawAsShadow = false,
            modifier = Modifier
                .size(side)
                .zIndex(Z_DIGITS)
                .boardLayer(
                    tilt = tilt,
                    density = density.density,
                    depth = DIGIT_DEPTH,
                ),
        )

        SparkField(
            sparks = sparks,
            layer = SparkDepthLayer.IN_FRONT_OF_DIGITS,
            phase = contentPhase,
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

                    val life = sparkLife(phase, spark.cycles, spark.offset)
                    val visibility = sparkVisibility(life)
                    val depthScale = (1f + spark.depth * SPARK_DEPTH_SCALE)
                        .coerceIn(SPARK_MIN_DEPTH_SCALE, SPARK_MAX_DEPTH_SCALE)
                    val depthAlpha = (SPARK_DEPTH_ALPHA_BASE + spark.depth * SPARK_DEPTH_ALPHA_STEP)
                        .coerceIn(SPARK_MIN_DEPTH_ALPHA, SPARK_MAX_DEPTH_ALPHA)
                    val travel = spark.depth * SPARK_PARALLAX_TRAVEL_DP * density
                    val radius = spark.radius * size.minDimension * depthScale
                    val centre = Offset(
                        x = (spark.x + sin(life * TAU + spark.offset * TAU) *
                            spark.drift * SPARK_SWAY) * size.width - yaw * travel,
                        y = (spark.startY - life * spark.rise) * size.height - pitch * travel,
                    )
                    val alpha = visibility * SPARK_MAX_ALPHA * depthAlpha

                    // A wide, faint halo reads as emitted light; the compact core keeps the
                    // particle crisp. Both evaporate together as the spark approaches the top.
                    drawCircle(
                        color = color,
                        radius = radius * SPARK_GLOW_RADIUS,
                        center = centre,
                        alpha = alpha * SPARK_GLOW_ALPHA,
                    )
                    drawCircle(
                        color = color,
                        radius = radius * (1f - life * SPARK_SHRINK),
                        center = centre,
                        alpha = alpha,
                    )
                }
            }
            .clearAndSetSemantics { },
    )
}

/** One complete 3x3 plane, used once for projected shadows and once for elevated digits. */
@Composable
private fun DigitGrid(
    sequences: List<List<Int>>,
    phase: Float,
    cell: Dp,
    color: Color,
    shadowOffset: Offset,
    drawAsShadow: Boolean,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        for (row in 0 until Sudoku.BLOCK_SIZE) {
            Row {
                for (column in 0 until Sudoku.BLOCK_SIZE) {
                    val index = row * Sudoku.BLOCK_SIZE + column
                    DriftingDigit(
                        sequence = sequences[index],
                        // Spread cells across the loop so all nine never breathe in unison.
                        cellPhase = phase * DIGIT_CYCLES + index * CELL_STAGGER,
                        cellSize = cell,
                        color = color,
                        shadowOffset = shadowOffset,
                        drawAsShadow = drawAsShadow,
                    )
                }
            }
        }
    }
}

/** One cell: a digit swelling into view and away again, taking the next of [sequence] each time. */
@Composable
private fun DriftingDigit(
    sequence: List<Int>,
    cellPhase: Float,
    cellSize: Dp,
    color: Color,
    shadowOffset: Offset,
    drawAsShadow: Boolean,
) {
    val turn = cellPhase.toInt()
    val within = cellPhase - turn

    // A bell still changes the numeral, but a high floor keeps it unmistakably above the board.
    val visibility = (1f - abs(within * 2f - 1f)).let { it * it }
    val alpha = DIGIT_MIN_ALPHA + visibility * (DIGIT_MAX_ALPHA - DIGIT_MIN_ALPHA)
    val textColor = if (drawAsShadow) {
        color.copy(alpha = alpha * DIGIT_CAST_SHADOW_FILL_ALPHA)
    } else {
        color.copy(alpha = alpha)
    }

    Box(
        modifier = Modifier
            .size(cellSize)
            .offset {
                IntOffset(shadowOffset.x.roundToInt(), shadowOffset.y.roundToInt())
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = sequence[turn % sequence.size].toString(),
            style = TextStyle(
                fontSize = with(LocalDensity.current) { (cellSize * DIGIT_HEIGHT_FRACTION).toSp() },
                fontWeight = FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                shadow = if (drawAsShadow) {
                    Shadow(
                        color = Color.Black.copy(alpha = alpha * DIGIT_CAST_SHADOW_ALPHA),
                        offset = Offset.Zero,
                        blurRadius = DIGIT_CAST_SHADOW_BLUR_PX,
                    )
                } else {
                    null
                },
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

internal const val CELLS = Sudoku.BLOCK_SIZE * Sudoku.BLOCK_SIZE
internal const val TAU = (2 * PI).toFloat()

/** Fast enough to feel alive, still slow enough to read as floating rather than spinning. */
private const val WOBBLE_CYCLE_MILLIS = 20_000

/** Digits and vapour use a slower independent clock so faster wobble does not make them frantic. */
private const val CONTENT_CYCLE_MILLIS = 36_000



private const val CAMERA_DISTANCE = 14f

private const val WIDTH_FRACTION = 0.44f
private const val HEIGHT_SAFE_FRACTION = 0.78f

/** Keeps the plate modest on a tablet, where 42% of the width would be a poster. */
private const val MAX_SIDE_DP = 180
private const val GRID_STROKE_DP = 1f
private const val GRID_ALPHA = 0.46f
private const val BOARD_EDGE_STROKE_DP = 1.25f
private const val BOARD_EDGE_ALPHA = 0.22f

private const val BOARD_BACKGROUND_LIFT = 0.13f
private const val DIGIT_COLOR_LIFT = 0.62f
private const val GRID_COLOR_LIFT = 0.48f

private const val DEPTH_SCALE = 0.025f
private const val MAX_DEPTH_TRAVEL_DP = 6f

private const val Z_REAR_SPARKS = 1f
private const val Z_BOARD = 2f
private const val Z_DIGIT_SHADOWS = 2.5f
private const val Z_MIDDLE_SPARKS = 3f
private const val Z_DIGITS = 4f
private const val Z_FRONT_SPARKS = 5f
private const val Z_INPUT = 6f

/** One full permutation per loop makes the digit layer continuous at the phase boundary. */
private const val DIGIT_CYCLES = 9f
private const val CELL_STAGGER = 0.37f
private const val DIGIT_HEIGHT_FRACTION = 0.42f
private const val DIGIT_MIN_ALPHA = 0.72f
private const val DIGIT_MAX_ALPHA = 1f
private const val DIGIT_CAST_SHADOW_FILL_ALPHA = 0.16f
private const val DIGIT_CAST_SHADOW_ALPHA = 0.42f
private const val DIGIT_CAST_SHADOW_BLUR_PX = 5f

/**
 * One drifting spark. Everything is a fraction of the drawing area, so the same description works
 * at any size, and everything is fixed at composition — the motion comes from the shared phase, so
 * the sparks stop with the rest of it when the app goes to the background.
 */
private data class Spark(
    val x: Float,
    val startY: Float,
    val drift: Float,
    val rise: Float,
    val radius: Float,
    val cycles: Int,
    val offset: Float,
    val depth: Float,
)

private const val SPARK_COUNT = 36
private const val SPARK_MIN_START_Y = 0.96f
private const val SPARK_START_Y_RANGE = 0.12f
private const val SPARK_MIN_RISE = 1.08f
private const val SPARK_RISE_RANGE = 0.28f
private const val SPARK_MIN_RADIUS = 0.004f
private const val SPARK_RADIUS_RANGE = 0.008f
private const val SPARK_MIN_CYCLES = 1
private const val SPARK_MAX_CYCLES = 2
private const val SPARK_SWAY = 0.075f
private const val SPARK_SHRINK = 0.60f
private const val SPARK_MAX_ALPHA = 0.64f
private const val SPARK_GLOW_RADIUS = 2.6f
private const val SPARK_GLOW_ALPHA = 0.20f
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
