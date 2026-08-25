package com.przunk.kratkoza.domain.sudoku

/**
 * Structural checks against the classic sudoku rules: a digit appears at most once per row, column
 * and 3x3 block (project.md section 5).
 *
 * This is about the rules alone. Whether a digit matches the puzzle's stored solution — which is
 * what project.md section 16 calls a mistake — is decided in `GameSession`, not here. A board can be
 * perfectly consistent and still be wrong.
 */
object SudokuValidator {

    /** True when no unit holds the same digit twice. Empty cells are ignored. */
    fun isConsistent(board: SudokuBoard): Boolean {
        for (unit in Sudoku.units) {
            var seen = 0
            for (cell in unit) {
                val value = board[cell]
                if (value == Sudoku.EMPTY) continue
                val bit = 1 shl value
                if (seen and bit != 0) return false
                seen = seen or bit
            }
        }
        return true
    }

    /** True when the board is completely filled and breaks no rule. */
    fun isSolved(board: SudokuBoard): Boolean = board.isComplete && isConsistent(board)

    /**
     * Every cell that shares a unit with another cell holding the same digit.
     *
     * Both sides of a clash are reported: with two 5s in a row there is no way to tell which one the
     * player meant, so highlighting only one of them would be arbitrary.
     */
    fun conflictingCells(board: SudokuBoard): Set<Int> {
        val conflicts = mutableSetOf<Int>()
        for (unit in Sudoku.units) {
            for (position in unit.indices) {
                val cell = unit[position]
                val value = board[cell]
                if (value == Sudoku.EMPTY) continue
                for (other in position + 1 until unit.size) {
                    if (board[unit[other]] == value) {
                        conflicts += cell
                        conflicts += unit[other]
                    }
                }
            }
        }
        return conflicts
    }

    /** True when [value] may be written into the empty cell [index] without breaking a rule. */
    fun canPlace(board: SudokuBoard, index: Int, value: Int): Boolean {
        if (!Sudoku.isDigit(value)) return false
        for (peer in Sudoku.peers[index]) {
            if (board[peer] == value) return false
        }
        return true
    }

    /**
     * The digits that [index] could still take given the digits already on the board.
     *
     * These are the "computed" candidates the app uses for auto-elimination and for the 8/9 hint —
     * not the candidates the player pencilled in, which live in the game state.
     */
    fun candidatesFor(board: SudokuBoard, index: Int): Set<Int> {
        if (!board.isEmpty(index)) return emptySet()
        return Sudoku.DIGITS.filterTo(mutableSetOf()) { canPlace(board, index, it) }
    }
}
