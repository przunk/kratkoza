package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.sudoku.DifficultyEvaluator
import com.przunk.kratkoza.domain.sudoku.LogicalSolver
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.domain.sudoku.SudokuSolver
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPuzzlesTest {

    private val generator = SudokuGenerator()
    private val date = LocalDate.parse("2026-08-26")

    @Test
    fun `everybody gets the same board for the same day`() {
        // project.md section 26, and the whole reason no server is needed. Two independent
        // generators stand in for two devices.
        for (difficulty in DailyPuzzles.DIFFICULTIES) {
            val here = DailyPuzzles.generate(SudokuGenerator(), date, difficulty)
            val there = DailyPuzzles.generate(SudokuGenerator(), date, difficulty)

            assertEquals("$difficulty", here.givens, there.givens)
            assertEquals(here.id, there.id)
        }
    }

    @Test
    fun `each day brings a different set`() {
        val today = DailyPuzzles.generate(generator, date, Difficulty.HARD)
        val tomorrow = DailyPuzzles.generate(generator, date.plusDays(1), Difficulty.HARD)

        assertNotEquals(today.givens, tomorrow.givens)
    }

    @Test
    fun `the three levels of one day are different boards`() {
        val boards = DailyPuzzles.DIFFICULTIES.map { DailyPuzzles.generate(generator, date, it).givens }
        assertEquals("no two levels may share a board", boards.size, boards.toSet().size)
    }

    @Test
    fun `the set is easiest, middle and hardest`() {
        assertEquals(listOf(Difficulty.EASY, Difficulty.HARD, Difficulty.MASTER), DailyPuzzles.DIFFICULTIES)
    }

    @Test
    fun `a daily board is a proper puzzle at the level it claims`() {
        for (difficulty in DailyPuzzles.DIFFICULTIES) {
            val puzzle = DailyPuzzles.generate(generator, date, difficulty)

            assertEquals(GameType.DAILY, puzzle.gameType)
            assertEquals(difficulty, puzzle.difficulty)
            assertTrue("$difficulty has no single solution", SudokuSolver.hasUniqueSolution(puzzle.givens))
            assertTrue("$difficulty needs guessing", LogicalSolver.solve(puzzle.givens).solved)
            assertEquals(difficulty, DifficultyEvaluator.evaluate(puzzle.givens)?.difficulty)
        }
    }

    @Test
    fun `the seed is stable, so a device that regenerates gets what it had`() {
        // Nothing about the seed may depend on the machine, the locale or the run. If this ever
        // fails, every past Daily has silently changed.
        assertEquals(
            DailyPuzzles.seedFor(date, Difficulty.EASY),
            DailyPuzzles.seedFor(date, Difficulty.EASY),
        )
        assertNotEquals(
            DailyPuzzles.seedFor(date, Difficulty.EASY),
            DailyPuzzles.seedFor(date, Difficulty.HARD),
        )
        assertNotEquals(
            DailyPuzzles.seedFor(date, Difficulty.EASY),
            DailyPuzzles.seedFor(date.plusDays(1), Difficulty.EASY),
        )
    }

    @Test
    fun `a run of days all produce valid sets`() {
        // Guards the generator's attempt limit: a seed that cannot reach its level would throw, and
        // a Daily that fails to appear is worse than one that is slightly off.
        var day = date
        repeat(7) {
            for (difficulty in DailyPuzzles.DIFFICULTIES) {
                val puzzle = DailyPuzzles.generate(generator, day, difficulty)
                assertEquals("$day $difficulty", difficulty, puzzle.difficulty)
            }
            day = day.plusDays(1)
        }
    }
}
