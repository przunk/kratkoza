package com.przunk.kratkoza.domain.sudoku

import com.przunk.kratkoza.domain.model.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalSolverTest {

    @Test
    fun `solves an easy puzzle with singles alone`() {
        val result = LogicalSolver.solve(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS))

        assertTrue(result.solved)
        assertEquals(SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION), result.board)
        assertTrue(
            "Unexpectedly needed ${result.hardestTechnique}",
            result.hardestTechnique!!.rank <= SolvingTechnique.HIDDEN_SINGLE.rank,
        )
    }

    @Test
    fun `reports which techniques it used`() {
        val result = LogicalSolver.solve(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS))

        assertTrue(result.techniqueUsage.isNotEmpty())
        assertTrue(result.techniqueUsage.values.all { it > 0 })
        assertEquals(result.techniqueUsage.keys.maxByOrNull { it.rank }, result.hardestTechnique)
    }

    @Test
    fun `an already solved board needs no technique at all`() {
        val result = LogicalSolver.solve(SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION))

        assertTrue(result.solved)
        assertTrue(result.techniqueUsage.isEmpty())
        assertEquals(null, result.hardestTechnique)
    }

    @Test
    fun `refuses a board that already breaks a rule`() {
        val result = LogicalSolver.solve(SudokuBoard.parse(SudokuFixtures.CONTRADICTORY))
        assertFalse(result.solved)
    }

    @Test
    fun `an empty board is not solvable by logic`() {
        // Every cell has all nine candidates, so no technique can fire. Guessing is out of scope
        // for this solver by design.
        assertFalse(LogicalSolver.solve(SudokuBoard.EMPTY).solved)
    }

    @Test
    fun `withholding the needed technique makes the puzzle unsolvable`() {
        // A board that genuinely needs locked candidates must stall when only singles are allowed,
        // otherwise the technique ceiling used for grading would mean nothing.
        val puzzle = generateRequiring(Difficulty.HARD)

        assertTrue(LogicalSolver.solve(puzzle, Difficulty.HARD.allowedTechniques).solved)
        assertFalse(LogicalSolver.solve(puzzle, Difficulty.NORMAL.allowedTechniques).solved)
    }

    @Test
    fun `every technique only ever makes correct deductions`() {
        // The strongest safety net for the technique implementations: a wrong elimination on a
        // uniquely solvable board leads either to a stall or to a grid that differs from the one
        // and only solution. Both are caught here, across all five technique ceilings.
        val generator = SudokuGenerator()
        val random = Random(20260825)

        for (difficulty in Difficulty.entries) {
            repeat(3) {
                val puzzle = generator.generate(difficulty, random = random)
                val logical = LogicalSolver.solve(puzzle.givens)

                assertTrue("$difficulty puzzle ${puzzle.id} stalled", logical.solved)
                assertEquals(
                    "$difficulty puzzle ${puzzle.id} solved to the wrong grid",
                    puzzle.solution,
                    logical.board,
                )
            }
        }
    }

    /** A board that needs [difficulty] exactly, so a lower ceiling is guaranteed to stall on it. */
    private fun generateRequiring(difficulty: Difficulty): SudokuBoard {
        val puzzle = SudokuGenerator().generate(difficulty, random = Random(7))
        assertNotNull(puzzle.seed)
        return puzzle.givens
    }
}
