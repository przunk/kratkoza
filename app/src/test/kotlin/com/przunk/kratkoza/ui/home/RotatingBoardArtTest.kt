package com.przunk.kratkoza.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingBoardArtTest {

    @Test
    fun `centre of the artwork keeps the board level`() {
        assertEquals(
            BoardTilt.Zero,
            boardTiltForPointer(x = 100f, y = 50f, width = 200, height = 100),
        )
    }

    @Test
    fun `opposite corners produce opposite bounded tilts`() {
        assertEquals(
            BoardTilt(pitch = TOUCH_PITCH_DEGREES, yaw = TOUCH_YAW_DEGREES),
            boardTiltForPointer(x = 0f, y = 0f, width = 200, height = 100),
        )
        assertEquals(
            BoardTilt(pitch = -TOUCH_PITCH_DEGREES, yaw = -TOUCH_YAW_DEGREES),
            boardTiltForPointer(x = 200f, y = 100f, width = 200, height = 100),
        )
    }

    @Test
    fun `a finger outside the artwork cannot over-rotate the board`() {
        assertEquals(
            BoardTilt(pitch = -TOUCH_PITCH_DEGREES, yaw = TOUCH_YAW_DEGREES),
            boardTiltForPointer(x = -500f, y = 500f, width = 200, height = 100),
        )
    }

    @Test
    fun `an unmeasured artwork has a safe neutral tilt`() {
        assertEquals(BoardTilt.Zero, boardTiltForPointer(10f, 10f, width = 0, height = 100))
        assertEquals(BoardTilt.Zero, boardTiltForPointer(10f, 10f, width = 100, height = 0))
    }

    @Test
    fun `release blend moves between the live touch and idle poses`() {
        val idle = BoardTilt(pitch = -4f, yaw = 8f)
        val touch = BoardTilt(pitch = 10f, yaw = -14f)

        assertEquals(idle, blendBoardTilt(idle, touch, touchWeight = -1f))
        assertEquals(BoardTilt(pitch = 3f, yaw = -3f), blendBoardTilt(idle, touch, 0.5f))
        assertEquals(touch, blendBoardTilt(idle, touch, touchWeight = 2f))
    }

    @Test
    fun `spark depth places particles around both content planes`() {
        assertEquals(SparkDepthLayer.BEHIND_BOARD, sparkDepthLayer(-0.01f))
        assertEquals(SparkDepthLayer.BETWEEN_BOARD_AND_DIGITS, sparkDepthLayer(0f))
        assertEquals(SparkDepthLayer.BETWEEN_BOARD_AND_DIGITS, sparkDepthLayer(0.99f))
        assertEquals(SparkDepthLayer.IN_FRONT_OF_DIGITS, sparkDepthLayer(1f))
    }

    @Test
    fun `the idle wobble leans as far one way as the other`() {
        // Phases are multiples of the fundamental's precisely so this holds. Chosen freely they do
        // not: yaw once ran to -15.5 degrees but only +10.2, which is what read on a device as the
        // board leaning permanently to one side.
        val samples = (0 until 2000).map { idleBoardTilt(it / 2000f) }

        val pitch = samples.map { it.pitch }
        val yaw = samples.map { it.yaw }
        assertEquals(-pitch.min(), pitch.max(), 0.01f)
        assertEquals(-yaw.min(), yaw.max(), 0.01f)
    }

    @Test
    fun `idle wobble joins seamlessly at the cycle boundary`() {
        val start = idleBoardTilt(0f)
        val end = idleBoardTilt(1f)

        assertEquals(start.pitch, end.pitch, 0.0001f)
        assertEquals(start.yaw, end.yaw, 0.0001f)
    }

    @Test
    fun `spark cycle joins seamlessly at shared phase boundary`() {
        assertEquals(
            sparkLife(phase = 0f, cycles = 2, offset = 0.37f),
            sparkLife(phase = 1f, cycles = 2, offset = 0.37f),
            0.0001f,
        )
    }

    @Test
    fun `spark ignites at bottom and evaporates towards top`() {
        assertEquals(0f, sparkVisibility(0f), 0f)
        assertTrue(sparkVisibility(0.12f) > sparkVisibility(0.65f))
        assertEquals(0f, sparkVisibility(1f), 0f)
    }

    @Test
    fun `digit shadow reacts to tilt on the board plane`() {
        val neutral = digitShadowOffsetForTilt(BoardTilt.Zero, density = 2f)
        val tilted = digitShadowOffsetForTilt(
            BoardTilt(pitch = TOUCH_PITCH_DEGREES, yaw = TOUCH_YAW_DEGREES),
            density = 2f,
        )

        assertTrue(tilted.x > neutral.x)
        assertTrue(tilted.y < neutral.y)
    }
}
