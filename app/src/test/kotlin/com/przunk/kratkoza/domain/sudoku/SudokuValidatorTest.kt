package com.przunk.kratkoza.domain.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuValidatorTest {

    private val easy = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
    private val solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)

    @Test
    fun `a partially filled board with no repeats is consistent`() {
        assertTrue(SudokuValidator.isConsistent(easy))
        assertTrue(SudokuValidator.isConsistent(SudokuBoard.EMPTY))
    }

    @Test
    fun `a repeated digit in a row is inconsistent`() {
        assertFalse(SudokuValidator.isConsistent(SudokuBoard.parse(SudokuFixtures.CONTRADICTORY)))
    }

    @Test
    fun `a repeated digit in a column is inconsistent`() {
        val clash = SudokuBoard.EMPTY
            .withValue(Sudoku.index(row = 0, column = 4), 7)
            .withValue(Sudoku.index(row = 5, column = 4), 7)
        assertFalse(SudokuValidator.isConsistent(clash))
    }

    @Test
    fun `a repeated digit in a block is inconsistent`() {
        val clash = SudokuBoard.EMPTY
            .withValue(Sudoku.index(row = 3, column = 3), 4)
            .withValue(Sudoku.index(row = 5, column = 5), 4)
        assertFalse(SudokuValidator.isConsistent(clash))
    }

    @Test
    fun `only a complete and consistent board counts as solved`() {
        assertTrue(SudokuValidator.isSolved(solution))
        assertFalse(SudokuValidator.isSolved(easy))
        assertFalse(SudokuValidator.isSolved(solution.withValue(0, Sudoku.EMPTY)))
    }

    @Test
    fun `both sides of a clash are reported`() {
        val first = Sudoku.index(row = 0, column = 0)
        val second = Sudoku.index(row = 0, column = 2)
        val clash = SudokuBoard.EMPTY.withValue(first, 5).withValue(second, 5)

        assertEquals(setOf(first, second), SudokuValidator.conflictingCells(clash))
    }

    @Test
    fun `a clean board reports no conflicts`() {
        assertTrue(SudokuValidator.conflictingCells(easy).isEmpty())
        assertTrue(SudokuValidator.conflictingCells(solution).isEmpty())
    }

    @Test
    fun `canPlace refuses a digit already present in a peer`() {
        // Row 0 of the easy board is "53..7....", so the empty cell at column 2 may not take 5.
        val target = Sudoku.index(row = 0, column = 2)
        assertFalse(SudokuValidator.canPlace(easy, target, 5))
        assertFalse(SudokuValidator.canPlace(easy, target, 3))
        assertTrue(SudokuValidator.canPlace(easy, target, 4))
    }

    @Test
    fun `canPlace refuses a value that is not a digit`() {
        assertFalse(SudokuValidator.canPlace(easy, index = 2, value = Sudoku.EMPTY))
        assertFalse(SudokuValidator.canPlace(easy, index = 2, value = 10))
    }

    @Test
    fun `candidatesFor lists exactly the digits that fit`() {
        val target = Sudoku.index(row = 0, column = 2)
        val candidates = SudokuValidator.candidatesFor(easy, target)

        assertEquals(Sudoku.DIGITS.filter { SudokuValidator.canPlace(easy, target, it) }.toSet(), candidates)
        assertTrue(SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)[target] in candidates)
    }

    @Test
    fun `a filled cell has no candidates`() {
        assertTrue(SudokuValidator.candidatesFor(easy, index = 0).isEmpty())
    }
}
