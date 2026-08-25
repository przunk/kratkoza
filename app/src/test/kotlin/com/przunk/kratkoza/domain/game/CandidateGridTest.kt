package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateGridTest {

    @Test
    fun `an empty grid holds nothing`() {
        assertFalse(CandidateGrid.EMPTY.hasAny)
        for (cell in 0 until Sudoku.CELL_COUNT) {
            assertTrue(CandidateGrid.EMPTY.isEmpty(cell))
            assertEquals(emptySet<Int>(), CandidateGrid.EMPTY[cell])
        }
    }

    @Test
    fun `toggle adds then removes`() {
        val added = CandidateGrid.EMPTY.toggle(0, 3)
        assertEquals(setOf(3), added[0])
        assertTrue(added.contains(0, 3))
        assertTrue(added.toggle(0, 3).isEmpty(0))
    }

    @Test
    fun `digits come back in ascending order`() {
        val grid = CandidateGrid.EMPTY.toggle(0, 9).toggle(0, 2).toggle(0, 5)
        assertEquals(listOf(2, 5, 9), grid[0].toList())
    }

    @Test
    fun `with replaces a cell wholesale`() {
        val grid = CandidateGrid.EMPTY.toggle(4, 1).with(4, setOf(7, 8))
        assertEquals(setOf(7, 8), grid[4])
    }

    @Test
    fun `changing one cell leaves the original grid alone`() {
        val original = CandidateGrid.EMPTY.toggle(10, 4)
        val changed = original.toggle(10, 5)

        assertEquals(setOf(4), original[10])
        assertEquals(setOf(4, 5), changed[10])
    }

    @Test
    fun `an operation that changes nothing returns the same instance`() {
        val grid = CandidateGrid.EMPTY.toggle(0, 1)
        assertSame(grid, grid.with(0, setOf(1)))
        assertSame(grid, grid.cleared(1))
        assertSame(grid, grid.removeDigitFrom(Sudoku.peers[0], 2))
    }

    @Test
    fun `removeDigitFrom clears exactly the cells given`() {
        var grid = CandidateGrid.EMPTY
        for (peer in Sudoku.peers[0]) grid = grid.toggle(peer, 6).toggle(peer, 7)
        grid = grid.toggle(40, 6)

        val stripped = grid.removeDigitFrom(Sudoku.peers[0], 6)

        for (peer in Sudoku.peers[0]) {
            assertFalse(stripped.contains(peer, 6))
            assertTrue(stripped.contains(peer, 7))
        }
        assertTrue("a cell outside the peer list must be untouched", stripped.contains(40, 6))
    }

    @Test
    fun `differingCells reports only what changed`() {
        val grid = CandidateGrid.EMPTY.toggle(3, 1)
        assertEquals(listOf(3), CandidateGrid.EMPTY.differingCells(grid))
        assertEquals(emptyList<Int>(), grid.differingCells(grid))
    }

    @Test
    fun `storage round trips`() {
        var grid = CandidateGrid.EMPTY
        grid = grid.with(0, setOf(1, 2, 3)).with(80, setOf(9)).with(40, (1..9).toSet())

        val text = grid.toStorageString()
        assertEquals(Sudoku.CELL_COUNT * 3, text.length)
        assertEquals(grid, CandidateGrid.parse(text))
    }

    @Test
    fun `of builds the same grid as repeated toggles`() {
        val perCell = List(Sudoku.CELL_COUNT) { if (it == 5) setOf(4, 6) else emptySet() }
        assertEquals(CandidateGrid.EMPTY.toggle(5, 4).toggle(5, 6), CandidateGrid.of(perCell))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects text of the wrong length`() {
        CandidateGrid.parse("000")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects a mask with bits outside one to nine`() {
        CandidateGrid.parse("fff" + "000".repeat(Sudoku.CELL_COUNT - 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toggle rejects a value that is not a digit`() {
        CandidateGrid.EMPTY.toggle(0, 0)
    }
}
