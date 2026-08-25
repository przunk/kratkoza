package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard

/**
 * Things worth noticing about a board that are neither rules nor moves: what the 8-of-9 hint would
 * say, and which units a move has just finished off.
 *
 * Pure functions over two boards, so both features are testable without a screen — the animations
 * and the hint are the visible half of these, and the visible half is the half that cannot be
 * tested here.
 */
object BoardInsights {

    /**
     * Cells the 8-of-9 hint should fill in, mapped to the digit it should show
     * (project.md section 17).
     *
     * A unit qualifies when exactly one of its cells is empty *and* the other eight already hold the
     * right digits. Both halves matter: a unit with eight filled cells one of which is wrong looks
     * the same from the board alone, and "the digit that is missing" would then be a lie. Checking
     * against the solution is what keeps the hint from ever pointing somewhere wrong.
     *
     * The same cell can qualify through its row and its column at once; the digit is the same
     * either way, so the map simply holds it once.
     */
    fun eightOfNineHints(board: SudokuBoard, solution: SudokuBoard): Map<Int, Int> {
        val hints = mutableMapOf<Int, Int>()

        for (unit in Sudoku.units) {
            var empty = -1
            var emptyCount = 0
            var correctCount = 0

            for (cell in unit) {
                when {
                    board.isEmpty(cell) -> {
                        empty = cell
                        emptyCount++
                    }
                    board[cell] == solution[cell] -> correctCount++
                }
            }

            if (emptyCount == 1 && correctCount == Sudoku.SIZE - 1) {
                hints[empty] = solution[empty]
            }
        }
        return hints
    }

    /**
     * The cells of every row, column or block that [after] finished and [before] had not
     * (project.md section 36).
     *
     * "Finished" means filled *and* correct, so a unit packed with wrong digits does not get
     * congratulated. Completing the last cell of the board finishes a row, a column and a block at
     * once, and the three overlap — the caller gets one set of cells, not three.
     */
    fun newlyCompletedCells(
        before: SudokuBoard,
        after: SudokuBoard,
        solution: SudokuBoard,
    ): Set<Int> {
        val cells = mutableSetOf<Int>()
        for (unit in Sudoku.units) {
            if (isComplete(after, unit, solution) && !isComplete(before, unit, solution)) {
                cells += unit.asList()
            }
        }
        return cells
    }

    private fun isComplete(board: SudokuBoard, unit: IntArray, solution: SudokuBoard): Boolean =
        unit.all { board[it] == solution[it] }
}
