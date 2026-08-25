package com.przunk.kratkoza.domain.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuBoardTest {

    @Test
    fun `parse and toStorageString round trip`() {
        val text = SudokuFixtures.EASY_GIVENS
        assertEquals(text, SudokuBoard.parse(text).toStorageString())
    }

    @Test
    fun `parse accepts zero and space as empty`() {
        val dots = SudokuBoard.parse(".".repeat(81))
        assertEquals(dots, SudokuBoard.parse("0".repeat(81)))
        assertEquals(dots, SudokuBoard.parse(" ".repeat(81)))
    }

    @Test
    fun `parse ignores line breaks so fixtures can be written as a grid`() {
        val laidOut = (0 until 9).joinToString("\n") { row ->
            SudokuFixtures.EASY_GIVENS.substring(row * 9, row * 9 + 9)
        }
        assertEquals(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS), SudokuBoard.parse(laidOut))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects a board of the wrong length`() {
        SudokuBoard.parse(".".repeat(80))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects unexpected characters`() {
        SudokuBoard.parse("x".repeat(81))
    }

    @Test
    fun `withValue leaves the original board untouched`() {
        val original = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        val changed = original.withValue(index = 2, value = 7)

        assertEquals(7, changed[2])
        assertNotEquals(7, original[2])
        assertNotSame(original, changed)
    }

    @Test
    fun `withValue returns the same instance when nothing changes`() {
        val board = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        assertTrue(board === board.withValue(index = 0, value = board[0]))
    }

    @Test
    fun `withValue accepts zero to clear a cell`() {
        val board = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS).withValue(0, Sudoku.EMPTY)
        assertTrue(board.isEmpty(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withValue rejects a digit outside one to nine`() {
        SudokuBoard.EMPTY.withValue(index = 0, value = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withValue rejects a cell index outside the board`() {
        SudokuBoard.EMPTY.withValue(index = 81, value = 1)
    }

    @Test
    fun `toIntArray hands out a copy`() {
        val board = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        val values = board.toIntArray()
        values[0] = 9
        assertNotEquals(9, board[0])
    }

    @Test
    fun `row and column access agrees with index access`() {
        val board = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        for (row in 0 until 9) {
            for (column in 0 until 9) {
                assertEquals(board[Sudoku.index(row, column)], board[row, column])
            }
        }
    }

    @Test
    fun `filledCount and isComplete describe the board`() {
        assertEquals(0, SudokuBoard.EMPTY.filledCount)
        assertFalse(SudokuBoard.EMPTY.isComplete)

        val solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)
        assertEquals(81, solution.filledCount)
        assertTrue(solution.isComplete)
    }
}
