package com.przunk.kratkoza.domain.sudoku

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGeneratorTest {

    private val generator = SudokuGenerator()

    @Test
    fun `every level produces a board that satisfies the project rules`() {
        val random = Random(2026)

        for (difficulty in Difficulty.entries) {
            val puzzle = generator.generate(difficulty, random = random)
            val message = "$difficulty puzzle ${puzzle.id}"

            assertEquals(message, difficulty, puzzle.difficulty)
            assertTrue(message, SudokuValidator.isSolved(puzzle.solution))
            assertTrue(message, SudokuSolver.hasUniqueSolution(puzzle.givens))
            assertTrue(message, LogicalSolver.solve(puzzle.givens).solved)
            assertEquals(message, difficulty, DifficultyEvaluator.evaluate(puzzle.givens)?.difficulty)
        }
    }

    @Test
    fun `the givens agree with the solution`() {
        val puzzle = generator.generate(Difficulty.NORMAL, random = Random(11))

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (!puzzle.givens.isEmpty(cell)) {
                assertEquals(puzzle.solution[cell], puzzle.givens[cell])
            }
        }
    }

    @Test
    fun `no level goes below the seventeen givens a unique board needs`() {
        val random = Random(11)
        for (difficulty in Difficulty.entries) {
            val puzzle = generator.generate(difficulty, random = random)
            assertTrue(
                "$difficulty produced ${puzzle.givenCount} givens",
                puzzle.givenCount >= difficulty.minimumGivens,
            )
        }
    }

    @Test
    fun `harder levels are not simply boards with fewer clues`() {
        // project.md section 5: the technique tier is the axis, not the clue count. A MASTER board
        // may well show more givens than a NORMAL one, and that is not a defect.
        val random = Random(5)
        val normal = generator.generate(Difficulty.NORMAL, random = random)
        val master = generator.generate(Difficulty.MASTER, random = random)

        assertEquals(Difficulty.NORMAL, DifficultyEvaluator.evaluate(normal.givens)?.difficulty)
        assertEquals(Difficulty.MASTER, DifficultyEvaluator.evaluate(master.givens)?.difficulty)
        assertTrue(
            "MASTER must need a technique NORMAL never does",
            !DifficultyEvaluator.isSolvableWithin(master.givens, Difficulty.NORMAL),
        )
    }

    @Test
    fun `the same seed reproduces the same puzzle`() {
        val first = generator.generateFromSeed(Difficulty.HARD, seed = 123456789L)
        val second = generator.generateFromSeed(Difficulty.HARD, seed = 123456789L)

        assertEquals(first, second)
        assertEquals(123456789L, first.seed)
    }

    @Test
    fun `different seeds produce different puzzles`() {
        val first = generator.generateFromSeed(Difficulty.NORMAL, seed = 1L)
        val second = generator.generateFromSeed(Difficulty.NORMAL, seed = 2L)

        assertNotEquals(first.givens, second.givens)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `the id is derived from the board and carries the generator version`() {
        val puzzle = generator.generateFromSeed(Difficulty.EASY, seed = 77L)

        assertEquals(SudokuGenerator.GENERATOR_VERSION, puzzle.generatorVersion)
        assertTrue(puzzle.id.startsWith("${SudokuGenerator.GENERATOR_VERSION}-"))
        assertEquals(PuzzleId.of(puzzle.generatorVersion, puzzle.givens, puzzle.solution), puzzle.id)
    }

    @Test
    fun `game type is carried onto the puzzle`() {
        val daily = generator.generateFromSeed(Difficulty.EASY, seed = 8L, gameType = GameType.DAILY)
        assertEquals(GameType.DAILY, daily.gameType)
    }

    @Test
    fun `removing any given would cost the board its unique solution or its level`() {
        // The generator digs until no further removal is allowed, so the result sits at that
        // boundary. This is what stops it from shipping a needlessly over-clued board.
        val puzzle = generator.generateFromSeed(Difficulty.MASTER, seed = 31L)

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (puzzle.givens.isEmpty(cell)) continue
            val reduced = puzzle.givens.withValue(cell, Sudoku.EMPTY)
            val stillAllowed = SudokuSolver.hasUniqueSolution(reduced) &&
                LogicalSolver.solve(reduced, Difficulty.MASTER.allowedTechniques).solved
            assertTrue("Clue at $cell was still removable", !stillAllowed)
        }
    }

    @Test
    fun `an easy board stops digging at its clue floor`() {
        val puzzle = generator.generateFromSeed(Difficulty.EASY, seed = 31L)
        assertEquals(Difficulty.EASY_MINIMUM_GIVENS, puzzle.givenCount)
    }
}
