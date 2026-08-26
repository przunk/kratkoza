package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private val cell = GameFixtures.EMPTY_CELL
    private val correct = GameFixtures.EMPTY_CELL_SOLUTION
    private val wrong = GameFixtures.EMPTY_CELL_WRONG_DIGIT

    @Test
    fun `a new game starts from the givens with nothing else set`() {
        val state = GameFixtures.newGame()

        assertEquals(GameFixtures.PUZZLE.givens, state.board)
        assertEquals(CandidateGrid.EMPTY, state.candidates)
        assertEquals(0, state.mistakeCount)
        assertFalse(state.canUndo)
        assertFalse(state.isSolved)
    }

    @Test
    fun `entering a correct digit costs no mistake`() {
        val state = GameEngine.enterDigit(GameFixtures.newGame(), cell, correct, autoRemoveCandidates = false)

        assertEquals(correct, state.board[cell])
        assertEquals(0, state.mistakeCount)
        assertTrue(state.incorrectCells.isEmpty())
    }

    @Test
    fun `a correct player digit cannot be replaced`() {
        val entered = GameEngine.enterDigit(
            GameFixtures.newGame(),
            cell,
            correct,
            autoRemoveCandidates = true,
        )

        val replacement = GameEngine.enterDigit(entered, cell, wrong, autoRemoveCandidates = true)

        assertSame(entered, replacement)
    }

    @Test
    fun `a correct player digit cannot be erased`() {
        val entered = GameEngine.enterDigit(
            GameFixtures.newGame(),
            cell,
            correct,
            autoRemoveCandidates = true,
        )

        assertSame(entered, GameEngine.clearCell(entered, cell))
    }

    @Test
    fun `correcting a wrong digit locks it without changing the recorded mistake`() {
        var state = GameEngine.enterDigit(
            GameFixtures.newGame(),
            cell,
            wrong,
            autoRemoveCandidates = false,
        )
        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = false)

        val replacement = GameEngine.enterDigit(state, cell, wrong, autoRemoveCandidates = false)

        assertSame(state, replacement)
        assertEquals(1, replacement.mistakeCount)
    }

    @Test
    fun `a restored correct player digit is locked without persisted lock state`() {
        val fresh = GameFixtures.newGame()
        val restored = fresh.copy(
            board = fresh.board.withValue(cell, correct),
        )

        assertSame(restored, GameEngine.enterDigit(restored, cell, wrong, autoRemoveCandidates = false))
        assertSame(restored, GameEngine.clearCell(restored, cell))
    }

    @Test
    fun `entering a wrong digit counts one mistake and marks the cell`() {
        val state = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)

        assertEquals(wrong, state.board[cell])
        assertEquals(1, state.mistakeCount)
        assertEquals(setOf(cell), state.incorrectCells)
    }

    @Test
    fun `each wrong digit counts again`() {
        // project.md section 16: every commit of a wrong digit is its own mistake.
        var state = GameFixtures.newGame()
        val otherWrong = (1..9).first { it != correct && it != wrong }

        state = GameEngine.enterDigit(state, cell, wrong, autoRemoveCandidates = false)
        state = GameEngine.enterDigit(state, cell, otherWrong, autoRemoveCandidates = false)

        assertEquals(2, state.mistakeCount)
    }

    @Test
    fun `correcting a wrong digit leaves the mistake on the record`() {
        var state = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)
        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = false)

        assertEquals(1, state.mistakeCount)
        assertTrue(state.incorrectCells.isEmpty())
    }

    @Test
    fun `re-entering the digit already in the cell changes nothing`() {
        val entered = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)
        val again = GameEngine.enterDigit(entered, cell, wrong, autoRemoveCandidates = false)

        assertSame(entered, again)
        assertEquals(1, again.mistakeCount)
    }

    @Test
    fun `a given never changes`() {
        val state = GameFixtures.newGame()
        val given = GameFixtures.GIVEN_CELL

        assertSame(state, GameEngine.enterDigit(state, given, 1, autoRemoveCandidates = true))
        assertSame(state, GameEngine.toggleCandidate(state, given, 1))
        assertSame(state, GameEngine.clearCell(state, given))
    }

    @Test
    fun `entering a digit clears that cell's own pencil marks`() {
        // project.md section 11.
        var state = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, 4)
        state = GameEngine.toggleCandidate(state, cell, 7)
        assertEquals(setOf(4, 7), state.candidates[cell])

        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = false)
        assertTrue(state.candidates.isEmpty(cell))
    }

    @Test
    fun `a correct digit strikes itself from every peer's pencil marks`() {
        // project.md section 15, default on.
        var state = GameFixtures.newGame()
        val peers = Sudoku.peers[cell].filter { state.board.isEmpty(it) }
        for (peer in peers) state = GameEngine.toggleCandidate(state, peer, correct)

        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = true)

        for (peer in peers) {
            assertFalse("peer $peer kept the candidate", state.candidates.contains(peer, correct))
        }
    }

    @Test
    fun `automatic removal leaves cells outside the row column and block alone`() {
        var state = GameFixtures.newGame()
        val outsider = (0 until Sudoku.CELL_COUNT).first {
            it !in Sudoku.peers[cell].toSet() && it != cell && state.board.isEmpty(it)
        }
        state = GameEngine.toggleCandidate(state, outsider, correct)
        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = true)

        assertTrue(state.candidates.contains(outsider, correct))
    }

    @Test
    fun `automatic removal can be switched off`() {
        var state = GameFixtures.newGame()
        val peer = Sudoku.peers[cell].first { state.board.isEmpty(it) }
        state = GameEngine.toggleCandidate(state, peer, correct)
        state = GameEngine.enterDigit(state, cell, correct, autoRemoveCandidates = false)

        assertTrue(state.candidates.contains(peer, correct))
    }

    @Test
    fun `a wrong digit never touches other cells' pencil marks`() {
        // Auto-removal follows a correct digit only; a wrong one must not wipe twenty cells of
        // correct notes that the player would then have to rebuild by hand.
        var state = GameFixtures.newGame()
        val peer = Sudoku.peers[cell].first { state.board.isEmpty(it) }
        state = GameEngine.toggleCandidate(state, peer, wrong)
        state = GameEngine.enterDigit(state, cell, wrong, autoRemoveCandidates = true)

        assertTrue(state.candidates.contains(peer, wrong))
    }

    @Test
    fun `toggling a candidate adds it and toggling again removes it`() {
        var state = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, 5)
        assertEquals(setOf(5), state.candidates[cell])

        state = GameEngine.toggleCandidate(state, cell, 5)
        assertTrue(state.candidates.isEmpty(cell))
    }

    @Test
    fun `a candidate is never a mistake`() {
        // project.md section 14: pencil marks are a normal technique, not a wrong answer.
        val state = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, wrong)

        assertEquals(0, state.mistakeCount)
        assertTrue(state.incorrectCells.isEmpty())
    }

    @Test
    fun `a cell holding a digit takes no pencil marks`() {
        val state = GameEngine.enterDigit(GameFixtures.newGame(), cell, correct, autoRemoveCandidates = false)
        assertSame(state, GameEngine.toggleCandidate(state, cell, 3))
    }

    @Test
    fun `erasing empties the cell`() {
        var state = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)
        state = GameEngine.clearCell(state, cell)

        assertTrue(state.board.isEmpty(cell))
        assertTrue(state.candidates.isEmpty(cell))
    }

    @Test
    fun `erasing clears pencil marks too`() {
        var state = GameEngine.toggleCandidate(GameFixtures.newGame(), cell, 2)
        state = GameEngine.toggleCandidate(state, cell, 8)
        state = GameEngine.clearCell(state, cell)

        assertTrue(state.candidates.isEmpty(cell))
    }

    @Test
    fun `erasing an already empty cell changes nothing`() {
        val state = GameFixtures.newGame()
        assertSame(state, GameEngine.clearCell(state, cell))
    }

    @Test
    fun `erasing does not undo the mistake it removes from the board`() {
        var state = GameEngine.enterDigit(GameFixtures.newGame(), cell, wrong, autoRemoveCandidates = false)
        state = GameEngine.clearCell(state, cell)

        assertEquals(1, state.mistakeCount)
    }
}
