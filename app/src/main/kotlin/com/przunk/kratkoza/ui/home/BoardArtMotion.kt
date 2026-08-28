package com.przunk.kratkoza.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.sin

/*
 * The motion behind the title artwork, with no Compose state and nothing drawn.
 *
 * Split out of RotatingBoardArt, which had grown past six hundred lines. The seam was already there
 * and already tested: these are the functions RotatingBoardArtTest exercises, and they are the ones
 * worth reasoning about — the rest of that file is placement and paint.
 */

internal const val TOUCH_YAW_DEGREES = 14f
internal const val TOUCH_PITCH_DEGREES = 10f
internal const val IDLE_PITCH_DEGREES = 9f
internal const val IDLE_YAW_DEGREES = 16f
/** Offsets pitch against yaw so the motion is a figure, not a line along one diagonal. */
internal const val PITCH_PHASE = 1.30f
internal const val MAX_VISUAL_PITCH_DEGREES = 10f
internal const val MAX_VISUAL_YAW_DEGREES = 16f
internal const val BOARD_DEPTH = 0f
internal const val DIGIT_DEPTH = 1f
internal const val DIGIT_SHADOW_BASE_X_DP = 1.4f
internal const val DIGIT_SHADOW_BASE_Y_DP = 2.2f
internal const val DIGIT_SHADOW_TILT_X_DP = 1.8f
internal const val DIGIT_SHADOW_TILT_Y_DP = 1.4f
internal const val SPARK_FADE_IN_FRACTION = 0.10f
internal const val SPARK_EVAPORATION_START = 0.22f

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

/**
 * Demo-scene wobble: integer harmonics make position and velocity meet at the loop boundary.
 * The result only tilts within a small range; it never accumulates into a full rotation.
 *
 * Every phase is the matching multiple of the fundamental's, which is exactly the condition for the
 * swing to reach as far one way as the other: the whole wave is then a time shift of a zero-phase
 * one, and a time shift cannot make extremes uneven. Phases chosen freely do not have that property
 * — yaw used to run to -15.5 degrees but only +10.2, which read as the board leaning one way.
 *
 * Pitch keeps a phase of its own so the two axes trace a figure rather than a diagonal; only its
 * relation to yaw matters, not its value.
 */
internal fun idleBoardTilt(phase: Float): BoardTilt {
    val angle = phase * TAU
    return BoardTilt(
        pitch = (
            sin(angle + PITCH_PHASE) * 0.68f +
                sin(angle * 2f + PITCH_PHASE * 2f) * 0.22f +
                sin(angle * 4f + PITCH_PHASE * 4f) * 0.10f
            ) * IDLE_PITCH_DEGREES,
        yaw = (
            sin(angle) * 0.72f +
                sin(angle * 2f) * 0.20f +
                sin(angle * 3f) * 0.08f
            ) * IDLE_YAW_DEGREES,
    )
}

/** Projects the elevated digits towards a fixed top-left light onto the board below. */
internal fun digitShadowOffsetForTilt(tilt: BoardTilt, density: Float): Offset {
    val yaw = (tilt.yaw / MAX_VISUAL_YAW_DEGREES).coerceIn(-1f, 1f)
    val pitch = (tilt.pitch / MAX_VISUAL_PITCH_DEGREES).coerceIn(-1f, 1f)
    return Offset(
        x = (DIGIT_SHADOW_BASE_X_DP + yaw * DIGIT_SHADOW_TILT_X_DP) * density,
        y = (DIGIT_SHADOW_BASE_Y_DP - pitch * DIGIT_SHADOW_TILT_Y_DP) * density,
    )
}

/** Individual integer cycles keep every particle continuous when the shared phase restarts. */
internal fun sparkLife(phase: Float, cycles: Int, offset: Float): Float {
    val progress = phase * cycles + offset
    return progress - floor(progress)
}

/** Quick ignition at the bottom followed by a long evaporation towards the top. */
internal fun sparkVisibility(life: Float): Float {
    val progress = life.coerceIn(0f, 1f)
    val fadeIn = (progress / SPARK_FADE_IN_FRACTION).coerceIn(0f, 1f)
    val evaporation = (
        1f - ((progress - SPARK_EVAPORATION_START) / (1f - SPARK_EVAPORATION_START))
            .coerceIn(0f, 1f)
        )
    return fadeIn * evaporation * evaporation
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
