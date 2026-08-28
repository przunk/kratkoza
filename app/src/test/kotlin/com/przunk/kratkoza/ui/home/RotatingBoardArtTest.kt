package com.przunk.kratkoza.ui.home

import org.junit.Assert.assertEquals
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
}
