package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** project.md section 12 — everything undo has to cover, and the one thing it must not. */
class UndoTest {

    private val cell = GameFixtures.EMPTY_CELL
    private val correct = GameFixtures.EMPTY_CELL_SOLUTION
    private val wrong = GameFixtures.EMPTY_CELL_WRONG_DIGIT

    @Test
    fun `undo on a fresh game does nothing`() {
        val state = GameFixtures.newGame()
        assertSame(state, GameEngine.undo(state))
    }

    @Test
    fun `undo takes back entering a digit`() {
        val start = GameFixtures.newGame()
        val entered = GameEngine.enterDigit(start, cell, correct, autoRemoveCandidates = true)

        assertEquals(start.board, GameEngine.undo(entered).board)
        assertFalse(GameEngine.undo(entered).canUndo)
    }

    @Test
    fun `undo takes back changing a digit`() {
        var state = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)
        val beforeChange = state
        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = false)

        assertEquals(beforeChange.board, GameEngine.undo(state).board)
    }

    @Test
    fun `undo takes back erasing a digit`() {
        val entered = GameEngine.enterDigit(GameFixtures.newGame(), cell, correct, autoRemoveCandidates = false)
        val erased = GameEngine.clearCell(entered, cell)

        assertEquals(correct, GameEngine.undo(erased).board[cell])
    }

    @Test
    fun `undo takes back adding a candidate`() {
        val added = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, 6)
        assertTrue(GameEngine.undo(added).candidates.isEmpty(cell))
    }

    @Test
    fun `undo takes back removing a candidate`() {
        val added = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, 6)
        val removed = GameEngine.toggleCandidate(added, cell, 6)

        assertEquals(setOf(6), GameEngine.undo(removed).candidates[cell])
    }

    @Test
    fun `undo restores candidates that automatic removal took away`() {
        // The hardest case in section 12: one move changed up to twenty other cells, and all of
        // them have to come back.
        var state = GameFixtures.newGame()
        val peers = Sudoku.peers[cell].filter { state.board.isEmpty(it) }
        for (peer in peers) state = GameEngine.toggleCandidate(state, peer, correct)

        val beforeEntry = state
        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = true)
        val undone = GameEngine.undo(state)

        assertEquals(beforeEntry.candidates, undone.candidates)
        assertEquals(beforeEntry.board, undone.board)
    }

    @Test
    fun `undo restores the erased cell's candidates`() {
        var state = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, 1)
        state = GameEngine.toggleCandidate(state, cell, 9)
        val beforeErase = state

        val undone = GameEngine.undo(GameEngine.clearCell(state, cell))
        assertEquals(beforeErase.candidates, undone.candidates)
    }

    @Test
    fun `undo never takes back a mistake`() {
        // project.md section 12: undo restores the board, but a mistake already made stays made.
        val entered = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)
        val undone = GameEngine.undo(entered)

        assertEquals(1, undone.mistakeCount)
        assertTrue(undone.board.isEmpty(cell))
    }

    @Test
    fun `undoing every move returns exactly to the starting position`() {
        val start = GameFixtures.newGame()
        var state = start
        val moves = 12

        var target = cell
        repeat(moves) { step ->
            target = (0 until Sudoku.CELL_COUNT).first {
                it >= target && start.board.isEmpty(it)
            }
            state = when (step % 3) {
                0 -> GameEngine.enterDigit(state, target, state.puzzle.solution[target], autoRemoveCandidates = true)
                1 -> GameEngine.toggleCandidate(state, target, 1 + step % 9)
                else -> GameEngine.clearCell(state, target)
            }
            target++
        }

        assertTrue(state.canUndo)
        repeat(state.undoStack.size) { state = GameEngine.undo(state) }

        assertEquals(start.board, state.board)
        assertEquals(start.candidates, state.candidates)
        assertFalse(state.canUndo)
    }

    @Test
    fun `the undo stack pops one move at a time`() {
        var state = GameFixtures.newGame()
        state = GameEngine.toggleCandidate(state, cell, 1)
        state = GameEngine.toggleCandidate(state, cell, 2)
        assertEquals(2, state.undoStack.size)

        state = GameEngine.undo(state)
        assertEquals(1, state.undoStack.size)
        assertEquals(setOf(1), state.candidates[cell])
    }
}
