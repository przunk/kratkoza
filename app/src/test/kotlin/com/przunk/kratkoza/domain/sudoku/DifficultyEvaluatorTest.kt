package com.przunk.kratkoza.domain.sudoku

import com.przunk.kratkoza.domain.model.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyEvaluatorTest {

    @Test
    fun `a singles-only board exactly on the line grades as easy`() {
        // The fixture carries exactly EASY_MINIMUM_GIVENS clues, which makes it the sharpest test of
        // the split available: the line is inclusive, and an off-by-one here would move every board
        // at the boundary into the wrong level.
        val givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        val rating = requireNotNull(DifficultyEvaluator.evaluate(givens))

        assertTrue(rating.hardestTechnique.rank <= SolvingTechnique.HIDDEN_SINGLE.rank)
        assertEquals(Difficulty.EASY_MINIMUM_GIVENS, givens.filledCount)
        assertEquals(Difficulty.EASY, rating.difficulty)
    }

    @Test
    fun `the same board with more of it filled in grades as easy`() {
        // Same techniques, more givens. This is exactly the line EASY and NORMAL are split on, so
        // it is worth pinning down with a board whose solution is known by hand.
        val solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)
        var givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
        var cell = 0
        while (givens.filledCount < Difficulty.EASY_MINIMUM_GIVENS) {
            if (givens.isEmpty(cell)) givens = givens.withValue(cell, solution[cell])
            cell++
        }

        assertEquals(Difficulty.EASY, DifficultyEvaluator.evaluate(givens)?.difficulty)
    }

    @Test
    fun `returns null for a board logic cannot finish`() {
        assertNull(DifficultyEvaluator.evaluate(SudokuBoard.EMPTY))
        assertNull(DifficultyEvaluator.evaluate(SudokuBoard.parse(SudokuFixtures.CONTRADICTORY)))
    }

    @Test
    fun `the grade of a generated puzzle matches what was asked for`() {
        val generator = SudokuGenerator()
        val random = Random(99)

        for (difficulty in Difficulty.entries) {
            val puzzle = generator.generate(difficulty, random = random)
            val rating = DifficultyEvaluator.evaluate(puzzle.givens)

            assertEquals("puzzle ${puzzle.id}", difficulty, rating?.difficulty)
        }
    }

    @Test
    fun `isSolvableWithin agrees with the grade`() {
        val puzzle = SudokuGenerator().generate(Difficulty.EXPERT, random = Random(3))

        assertTrue(DifficultyEvaluator.isSolvableWithin(puzzle.givens, Difficulty.EXPERT))
        assertTrue(DifficultyEvaluator.isSolvableWithin(puzzle.givens, Difficulty.MASTER))
        assertFalse(DifficultyEvaluator.isSolvableWithin(puzzle.givens, Difficulty.HARD))
    }

    @Test
    fun `grading follows the hardest technique the board needs`() {
        val generator = SudokuGenerator()

        for (difficulty in listOf(Difficulty.HARD, Difficulty.EXPERT, Difficulty.MASTER)) {
            val puzzle = generator.generate(difficulty, random = Random(difficulty.ordinal + 1L))
            val rating = requireNotNull(DifficultyEvaluator.evaluate(puzzle.givens))

            assertEquals(difficulty, rating.difficulty)
            assertTrue(
                "$difficulty graded on ${rating.hardestTechnique}",
                rating.hardestTechnique.rank <= difficulty.hardestRequiredTechnique.rank,
            )
            assertTrue(
                "$difficulty did not actually need its own tier",
                rating.hardestTechnique.rank > difficulty.easier!!.hardestRequiredTechnique.rank,
            )
        }
    }

    @Test
    fun `the two singles levels are told apart by how many givens they show`() {
        // EASY and NORMAL share a technique ceiling on purpose; see Difficulty's documentation.
        val generator = SudokuGenerator()
        val easy = generator.generate(Difficulty.EASY, random = Random(4))
        val normal = generator.generate(Difficulty.NORMAL, random = Random(4))

        assertTrue(easy.givenCount >= Difficulty.EASY_MINIMUM_GIVENS)
        assertTrue(normal.givenCount < Difficulty.EASY_MINIMUM_GIVENS)
        assertEquals(
            Difficulty.EASY.hardestRequiredTechnique,
            Difficulty.NORMAL.hardestRequiredTechnique,
        )
    }

    @Test
    fun `each level allows everything the level below allows`() {
        for (index in 1 until Difficulty.entries.size) {
            val easier = Difficulty.entries[index - 1]
            val harder = Difficulty.entries[index]
            assertTrue(harder.allowedTechniques.containsAll(easier.allowedTechniques))
            assertEquals(easier, harder.easier)
        }
        assertEquals(SolvingTechnique.ALL, Difficulty.MASTER.allowedTechniques)
        assertEquals(null, Difficulty.EASY.easier)
    }
}
