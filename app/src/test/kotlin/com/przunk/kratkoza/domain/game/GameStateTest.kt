package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

    @Test
    fun `a game is only solved when the board matches the solution`() {
        var state = GameFixtures.newGame()
        assertFalse(state.isSolved)

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (state.board.isEmpty(cell)) {
                state = GameEngine.enterDigit(
                    state,
                    cell,
                    state.puzzle.solution[cell],
                    autoRemoveCandidates = true,
                )
            }
        }
        assertTrue(state.isSolved)
    }

    @Test
    fun `a full board that is wrong somewhere is not solved`() {
        // project.md section 16: the game cannot count as finished until the board is correct.
        var state = GameFixtures.newGame()
        val firstEmpty = (0 until Sudoku.CELL_COUNT).first { state.board.isEmpty(it) }

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (!state.board.isEmpty(cell)) continue
            val digit = if (cell == firstEmpty) {
                (1..9).first { it != state.puzzle.solution[cell] }
            } else {
                state.puzzle.solution[cell]
            }
            state = GameEngine.enterDigit(state, cell, digit, autoRemoveCandidates = false)
        }

        assertTrue(state.board.isComplete)
        assertFalse(state.isSolved)
        assertEquals(setOf(firstEmpty), state.incorrectCells)
    }

    @Test
    fun `remaining counts start at nine minus the givens and fall to zero`() {
        var state = GameFixtures.newGame()
        for (digit in 1..9) {
            val givens = (0 until Sudoku.CELL_COUNT).count { state.puzzle.givens[it] == digit }
            assertEquals(9 - givens, state.remainingCountOf(digit))
        }

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (state.board.isEmpty(cell)) {
                state = GameEngine.enterDigit(state, cell, state.puzzle.solution[cell], autoRemoveCandidates = false)
            }
        }
        for (digit in 1..9) assertEquals(0, state.remainingCountOf(digit))
    }

    @Test
    fun `a repeated digit is reported as a conflict`() {
        var state = GameFixtures.newGame()
        val given = GameFixtures.GIVEN_CELL
        val target = Sudoku.peers[given].first { state.board.isEmpty(it) }

        state = GameEngine.enterDigit(state, target, state.board[given], autoRemoveCandidates = false)

        assertTrue(target in state.conflictingCells)
        assertTrue(given in state.conflictingCells)
    }

    @Test
    fun `a wrong digit does not count towards its nine`() {
        // project.md section 19 counts *correct* occurrences. Counting a wrong copy would call the
        // digit finished while a cell still needed it.
        var state = GameFixtures.newGame()
        val cell = (0 until Sudoku.CELL_COUNT).first { state.board.isEmpty(it) }
        val correct = state.puzzle.solution[cell]
        val wrongPlace = (0 until Sudoku.CELL_COUNT).first {
            state.board.isEmpty(it) && state.puzzle.solution[it] != correct
        }
        val before = state.remainingCountOf(correct)

        state = GameEngine.enterDigit(state, wrongPlace, correct, autoRemoveCandidates = false)

        assertEquals(before, state.remainingCountOf(correct))
        assertFalse(state.isDigitComplete(correct))
    }

    @Test
    fun `a digit is complete only when all nine are correctly placed`() {
        var state = GameFixtures.newGame()
        val digit = 5
        assertFalse(state.isDigitComplete(digit))

        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (state.board.isEmpty(cell) && state.puzzle.solution[cell] == digit) {
                state = GameEngine.enterDigit(state, cell, digit, autoRemoveCandidates = false)
            }
        }

        assertTrue(state.isDigitComplete(digit))
        assertEquals(0, state.remainingCountOf(digit))
    }
}
