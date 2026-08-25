package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardInsightsTest {

    private val solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)

    /** The solution with [cells] emptied out. */
    private fun boardMissing(vararg cells: Int): SudokuBoard =
        cells.fold(solution) { board, cell -> board.withValue(cell, Sudoku.EMPTY) }

    @Test
    fun `a unit missing exactly one digit is hinted`() {
        // project.md section 17.
        val target = Sudoku.index(row = 0, column = 4)
        val hints = BoardInsights.eightOfNineHints(boardMissing(target), solution)

        assertEquals(mapOf(target to solution[target]), hints)
    }

    @Test
    fun `a cell whose row, column and block each miss two digits is not hinted`() {
        // A 2x2 hole. Emptying two cells of one row is not enough: each of them is still the only
        // gap in its own column, and the column hints it. All three of a cell's units have to be
        // short of two digits before it goes quiet.
        val hole = intArrayOf(
            Sudoku.index(0, 0), Sudoku.index(0, 1),
            Sudoku.index(1, 0), Sudoku.index(1, 1),
        )
        assertTrue(BoardInsights.eightOfNineHints(boardMissing(*hole), solution).isEmpty())
    }

    @Test
    fun `two gaps in a row still leave each of them hinted by its column`() {
        val row = Sudoku.rows[0]
        val hints = BoardInsights.eightOfNineHints(boardMissing(row[1], row[5]), solution)

        assertEquals(setOf(row[1], row[5]), hints.keys)
        assertEquals(solution[row[1]], hints[row[1]])
    }

    @Test
    fun `a wrong digit in the unit withholds the hint`() {
        // Eight filled cells, one of them wrong, one empty. From the board alone that looks exactly
        // like a hintable unit, and the hint would name a digit that does not belong there.
        //
        // The target is set up so its row is the only unit that could hint it: its column and block
        // both carry a second gap. The same board is then checked with and without the wrong digit,
        // so the test turns on that digit alone.
        val target = Sudoku.index(row = 3, column = 0)
        val spoiled = Sudoku.index(row = 3, column = 1)
        val secondGap = Sudoku.index(row = 5, column = 0)

        val clean = boardMissing(target, secondGap)
        assertEquals(solution[target], BoardInsights.eightOfNineHints(clean, solution)[target])

        val spoiledBoard = clean.withValue(spoiled, (1..9).first { it != solution[spoiled] })
        assertNull(BoardInsights.eightOfNineHints(spoiledBoard, solution)[target])
    }

    @Test
    fun `an empty board offers nothing`() {
        assertTrue(BoardInsights.eightOfNineHints(SudokuBoard.EMPTY, solution).isEmpty())
    }

    @Test
    fun `a solved board offers nothing`() {
        assertTrue(BoardInsights.eightOfNineHints(solution, solution).isEmpty())
    }

    @Test
    fun `a cell qualifying through both its row and its column is listed once`() {
        val cell = Sudoku.index(row = 4, column = 4)
        val hints = BoardInsights.eightOfNineHints(boardMissing(cell), solution)

        assertEquals(1, hints.size)
        assertEquals(solution[cell], hints[cell])
    }

    @Test
    fun `finishing a row reports exactly that row`() {
        val cell = Sudoku.rows[2][7]
        val before = boardMissing(cell)

        val cells = BoardInsights.newlyCompletedCells(before, solution, solution)

        // The last cell of a row is also the last of its column and its block, so all three land.
        assertTrue(Sudoku.rows[2].all { it in cells })
        assertTrue(Sudoku.columns[Sudoku.columnOf(cell)].all { it in cells })
        assertTrue(Sudoku.blocks[Sudoku.blockOf(cell)].all { it in cells })
    }

    @Test
    fun `a move that finishes nothing reports nothing`() {
        // The filled cell has to leave a gap in each of its three units, or one of them finishes.
        val filled = Sudoku.index(0, 0)
        val before = boardMissing(filled, Sudoku.index(0, 1), Sudoku.index(1, 0), Sudoku.index(1, 1))
        val after = before.withValue(filled, solution[filled])

        assertTrue(BoardInsights.newlyCompletedCells(before, after, solution).isEmpty())
    }

    @Test
    fun `an already finished unit is not reported again`() {
        val elsewhere = Sudoku.index(row = 8, column = 8)
        val before = boardMissing(elsewhere)
        val after = before.withValue(elsewhere, solution[elsewhere])

        val cells = BoardInsights.newlyCompletedCells(after, after, solution)
        assertTrue(cells.isEmpty())
    }

    @Test
    fun `a unit filled with a wrong digit is not congratulated`() {
        val cell = Sudoku.rows[5][4]
        val before = boardMissing(cell)
        val after = before.withValue(cell, (1..9).first { it != solution[cell] })

        assertTrue(BoardInsights.newlyCompletedCells(before, after, solution).isEmpty())
    }
}
