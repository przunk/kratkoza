package com.przunk.kratkoza.domain.sudoku

import kotlin.random.Random

/**
 * Brute-force solving and solution counting.
 *
 * Backtracking with constraint propagation: each cell keeps a bitmask of the digits still open, and
 * the search always descends into the cell with the fewest of them (minimum remaining values). That
 * turns the uniqueness check — which the generator runs once per removed clue — from seconds into
 * microseconds, which is the difference between a usable generator and an unusable one.
 *
 * This is the *mechanical* solver. It answers "does a solution exist and is it unique". It says
 * nothing about whether a human could find it; that is [LogicalSolver]'s job.
 */
object SudokuSolver {

    /** All nine digits set, as bits 1..9. */
    private const val ALL_DIGITS = 0b11_1111_1110

    /**
     * One solution of [board], or `null` when it has none.
     *
     * [random] shuffles the order digits are tried in, which is what makes the generator produce a
     * different grid every time. Pass `null` for the deterministic ascending order.
     */
    fun solve(board: SudokuBoard, random: Random? = null): SudokuBoard? {
        val state = SearchState.from(board) ?: return null
        return if (state.search(random)) SudokuBoard.of(state.cells) else null
    }

    /**
     * How many solutions [board] has, counting no further than [limit].
     *
     * Capping matters: an empty grid has more than 6·10^21 solutions, so an uncapped count never
     * returns. Callers asking about uniqueness only ever need to tell 0 from 1 from "more than 1".
     */
    fun countSolutions(board: SudokuBoard, limit: Int = 2): Int {
        require(limit >= 1) { "limit must be at least 1" }
        val state = SearchState.from(board) ?: return 0
        return state.count(limit)
    }

    /**
     * True when [board] has exactly one solution — the guarantee project.md section 5 makes about
     * every puzzle the app hands to a player.
     */
    fun hasUniqueSolution(board: SudokuBoard): Boolean = countSolutions(board, limit = 2) == 1

    /**
     * The search grid: the digits placed so far plus, for every empty cell, a bitmask of the digits
     * still allowed there.
     */
    private class SearchState(
        val cells: IntArray,
        val candidates: IntArray,
    ) {
        /** Writes [digit] into [cell] and strikes it from every peer. False if that empties a peer. */
        fun place(cell: Int, digit: Int): Boolean {
            cells[cell] = digit
            candidates[cell] = 0
            val bit = 1 shl digit
            for (peer in Sudoku.peers[cell]) {
                if (cells[peer] == digit) return false
                if (candidates[peer] and bit != 0) {
                    candidates[peer] = candidates[peer] and bit.inv()
                    if (cells[peer] == Sudoku.EMPTY && candidates[peer] == 0) return false
                }
            }
            return true
        }

        fun copy(): SearchState = SearchState(cells.copyOf(), candidates.copyOf())

        /** The empty cell with the fewest candidates, or `-1` when the grid is full. */
        private fun mostConstrainedCell(): Int {
            var best = -1
            var bestCount = Int.MAX_VALUE
            for (cell in 0 until Sudoku.CELL_COUNT) {
                if (cells[cell] != Sudoku.EMPTY) continue
                val count = Integer.bitCount(candidates[cell])
                if (count < bestCount) {
                    bestCount = count
                    best = cell
                    // Nothing beats a forced cell, so stop looking.
                    if (count == 1) break
                }
            }
            return best
        }

        /** Fills [cells] with a solution in place; false when this branch is dead. */
        fun search(random: Random?): Boolean {
            val cell = mostConstrainedCell()
            if (cell == -1) return true

            for (digit in digitsOf(candidates[cell], random)) {
                val branch = copy()
                if (branch.place(cell, digit) && branch.search(random)) {
                    branch.cells.copyInto(cells)
                    return true
                }
            }
            return false
        }

        /** Number of solutions reachable from this state, stopping once [limit] are found. */
        fun count(limit: Int): Int {
            val cell = mostConstrainedCell()
            if (cell == -1) return 1

            var found = 0
            for (digit in digitsOf(candidates[cell], random = null)) {
                val branch = copy()
                if (branch.place(cell, digit)) {
                    found += branch.count(limit - found)
                    if (found >= limit) return found
                }
            }
            return found
        }

        private fun digitsOf(mask: Int, random: Random?): IntArray {
            val digits = IntArray(Integer.bitCount(mask))
            var next = 0
            for (digit in Sudoku.DIGITS) {
                if (mask and (1 shl digit) != 0) digits[next++] = digit
            }
            if (random != null) digits.shuffle(random)
            return digits
        }

        companion object {
            /** `null` when [board] already breaks a rule, so no solution can exist. */
            fun from(board: SudokuBoard): SearchState? {
                val state = SearchState(
                    cells = IntArray(Sudoku.CELL_COUNT),
                    candidates = IntArray(Sudoku.CELL_COUNT) { ALL_DIGITS },
                )
                for (cell in 0 until Sudoku.CELL_COUNT) {
                    val digit = board[cell]
                    if (digit == Sudoku.EMPTY) continue
                    if (state.candidates[cell] and (1 shl digit) == 0) return null
                    if (!state.place(cell, digit)) return null
                }
                return state
            }
        }
    }
}

private fun IntArray.shuffle(random: Random) {
    for (position in size - 1 downTo 1) {
        val other = random.nextInt(position + 1)
        val held = this[position]
        this[position] = this[other]
        this[other] = held
    }
}
