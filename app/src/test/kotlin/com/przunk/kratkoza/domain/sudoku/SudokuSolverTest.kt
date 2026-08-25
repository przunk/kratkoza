package com.przunk.kratkoza.domain.sudoku

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSolverTest {

    @Test
    fun `solves a puzzle to its known solution`() {
        val solved = SudokuSolver.solve(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS))
        assertEquals(SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION), solved)
    }

    @Test
    fun `keeps every given in place`() {
        val givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        val solved = requireNotNull(SudokuSolver.solve(givens))

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (!givens.isEmpty(cell)) assertEquals(givens[cell], solved[cell])
        }
    }

    @Test
    fun `returns null for a board that breaks a rule`() {
        assertNull(SudokuSolver.solve(SudokuBoard.parse(SudokuFixtures.CONTRADICTORY)))
        assertEquals(0, SudokuSolver.countSolutions(SudokuBoard.parse(SudokuFixtures.CONTRADICTORY)))
    }

    @Test
    fun `fills an empty grid with a valid solution`() {
        val solved = requireNotNull(SudokuSolver.solve(SudokuBoard.EMPTY))
        assertTrue(SudokuValidator.isSolved(solved))
    }

    @Test
    fun `a random seed changes which solution an empty grid gets`() {
        val first = SudokuSolver.solve(SudokuBoard.EMPTY, Random(1))
        val second = SudokuSolver.solve(SudokuBoard.EMPTY, Random(2))
        assertNotNull(first)
        assertTrue(first != second)
    }

    @Test
    fun `the same seed reproduces the same solution`() {
        assertEquals(
            SudokuSolver.solve(SudokuBoard.EMPTY, Random(42)),
            SudokuSolver.solve(SudokuBoard.EMPTY, Random(42)),
        )
    }

    @Test
    fun `counting stops at the limit instead of enumerating an empty grid`() {
        assertEquals(2, SudokuSolver.countSolutions(SudokuBoard.EMPTY, limit = 2))
        assertEquals(5, SudokuSolver.countSolutions(SudokuBoard.EMPTY, limit = 5))
    }

    @Test
    fun `a proper puzzle has exactly one solution`() {
        val givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        assertEquals(1, SudokuSolver.countSolutions(givens))
        assertTrue(SudokuSolver.hasUniqueSolution(givens))
    }

    @Test
    fun `removing the wrong clue costs uniqueness`() {
        val givens = SudokuBoard.parse(SudokuFixtures.EASY_WITH_TWO_SOLUTIONS)
        assertEquals(2, SudokuSolver.countSolutions(givens))
        assertFalse(SudokuSolver.hasUniqueSolution(givens))
    }

    @Test
    fun `a complete solution counts as one solution`() {
        assertEquals(1, SudokuSolver.countSolutions(SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `counting rejects a limit below one`() {
        SudokuSolver.countSolutions(SudokuBoard.EMPTY, limit = 0)
    }
}
