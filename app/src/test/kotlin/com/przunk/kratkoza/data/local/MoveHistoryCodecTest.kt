package com.przunk.kratkoza.data.local

import com.przunk.kratkoza.domain.game.GameEngine
import com.przunk.kratkoza.domain.game.GameFixtures
import com.przunk.kratkoza.domain.game.Move
import com.przunk.kratkoza.domain.sudoku.Sudoku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * project.md section 22 requires the undo history to survive a restart, so the codec has to be
 * exactly reversible — a lossy round trip would leave the player with an undo button that lies.
 */
class MoveHistoryCodecTest {

    @Test
    fun `an empty stack round trips`() {
        assertEquals("", MoveHistoryCodec.encode(emptyList()))
        assertEquals(emptyList<Move>(), MoveHistoryCodec.decode(""))
    }

    @Test
    fun `a move without candidate changes round trips`() {
        val moves = listOf(Move(index = 40, previousValue = 0, newValue = 7, previousCandidates = emptyMap()))
        assertEquals(moves, MoveHistoryCodec.decode(MoveHistoryCodec.encode(moves)))
    }

    @Test
    fun `a move with candidate changes round trips`() {
        val moves = listOf(
            Move(
                index = 3,
                previousValue = 2,
                newValue = 0,
                previousCandidates = mapOf(3 to setOf(1, 5, 9), 12 to setOf(4), 80 to emptySet()),
            ),
        )
        assertEquals(moves, MoveHistoryCodec.decode(MoveHistoryCodec.encode(moves)))
    }

    @Test
    fun `several moves keep their order`() {
        val moves = listOf(
            Move(0, 0, 1, emptyMap()),
            Move(1, 1, 2, mapOf(1 to setOf(3))),
            Move(2, 2, 0, emptyMap()),
        )
        assertEquals(moves, MoveHistoryCodec.decode(MoveHistoryCodec.encode(moves)))
    }

    @Test
    fun `a real undo stack round trips`() {
        // The case that actually matters: automatic candidate removal writes a move touching up to
        // twenty cells, and all of them must come back.
        var state = GameFixtures.newGame()
        val cell = GameFixtures.EMPTY_CELL
        for (peer in Sudoku.peers[cell].filter { state.board.isEmpty(it) }) {
            state = GameEngine.toggleCandidate(state, peer, GameFixtures.EMPTY_CELL_SOLUTION)
        }
        state = GameEngine.enterDigit(state, cell, GameFixtures.EMPTY_CELL_SOLUTION, autoRemoveCandidates = true)

        val restored = MoveHistoryCodec.decode(MoveHistoryCodec.encode(state.undoStack))

        assertEquals(state.undoStack, restored)
        assertTrue(restored.last().previousCandidates.size > 1)
    }

    @Test
    fun `undo works the same after a round trip`() {
        var state = GameFixtures.newGame()
        val cell = GameFixtures.EMPTY_CELL
        state = GameEngine.toggleCandidate(state, cell, 4)
        state = GameEngine.enterDigit(state, cell, GameFixtures.EMPTY_CELL_SOLUTION, autoRemoveCandidates = true)

        val restored = state.copy(undoStack = MoveHistoryCodec.decode(MoveHistoryCodec.encode(state.undoStack)))

        assertEquals(GameEngine.undo(state), GameEngine.undo(restored))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed move is rejected rather than silently dropped`() {
        MoveHistoryCodec.decode("1,2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a cell index outside the board is rejected`() {
        MoveHistoryCodec.decode("81,0,1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a value that is not a digit is rejected`() {
        MoveHistoryCodec.decode("0,0,10")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed candidate entry is rejected`() {
        MoveHistoryCodec.decode("0,0,1,3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a candidate mask outside one to nine is rejected`() {
        MoveHistoryCodec.decode("0,0,1,3:4095")
    }
}
