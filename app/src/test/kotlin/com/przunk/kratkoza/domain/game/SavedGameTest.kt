package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What "partly filled" means for a saved game.
 *
 * The measure is the one thing here worth arguing about, so it is pinned: correctly filled cells out
 * of the cells that started empty.
 */
class SavedGameTest {

    private val givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)
    private val solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)

    private fun summaryOf(board: SudokuBoard) = SavedGameSummary(
        puzzleId = "test",
        difficulty = Difficulty.NORMAL,
        gameType = GameType.SOLO,
        startedAt = 0,
        lastPlayedAt = 0,
        elapsedMillis = 0,
        mistakeCount = 0,
        board = board,
        givens = givens,
        solution = solution,
        generatorVersion = 1,
    )

    private val emptyCells = (0 until Sudoku.CELL_COUNT).filter { givens.isEmpty(it) }

    @Test
    fun `an untouched board is at zero`() {
        assertEquals(0f, summaryOf(givens).completionFraction, 0f)
    }

    @Test
    fun `a solved board is at one`() {
        assertEquals(1f, summaryOf(solution).completionFraction, 0f)
    }

    @Test
    fun `the givens do not count towards progress`() {
        // Otherwise every puzzle would open at around seventy percent and the figure would say
        // nothing about the player.
        assertEquals(0f, summaryOf(givens).completionFraction, 0f)
    }

    @Test
    fun `wrong digits do not count`() {
        // The trap this measure avoids: filling the board with rubbish must not report it finished.
        var board = givens
        for (cell in emptyCells) {
            board = board.withValue(cell, (1..9).first { it != solution[cell] })
        }

        assertEquals(0f, summaryOf(board).completionFraction, 0f)
    }

    @Test
    fun `half the empty cells filled correctly reads as half`() {
        val half = emptyCells.size / 2
        var board = givens
        for (cell in emptyCells.take(half)) board = board.withValue(cell, solution[cell])

        assertEquals(
            half.toFloat() / emptyCells.size,
            summaryOf(board).completionFraction,
            0.0001f,
        )
    }

    @Test
    fun `a board with nothing to fill is complete rather than dividing by zero`() {
        val summary = SavedGameSummary(
            puzzleId = "test",
            difficulty = Difficulty.NORMAL,
            gameType = GameType.SOLO,
            startedAt = 0,
            lastPlayedAt = 0,
            elapsedMillis = 0,
            mistakeCount = 0,
            board = solution,
            givens = solution,
            solution = solution,
            generatorVersion = 1,
        )

        assertEquals(1f, summary.completionFraction, 0f)
    }
}
