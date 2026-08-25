package com.przunk.kratkoza.domain.sudoku

/**
 * Solves a puzzle the way a person would: by applying named techniques in order of difficulty,
 * never by guessing.
 *
 * Two things depend on this. project.md section 5 requires every generated puzzle to be solvable by
 * logic alone, which is exactly "this solver finishes it". And it requires difficulty to reflect the
 * techniques a puzzle demands, which is [SolvingTechnique.rank] of the hardest step taken here.
 *
 * The loop always retries from the cheapest technique after any progress. A puzzle is graded by the
 * hardest step it *forces*, so an expensive technique must only ever be reached when nothing cheaper
 * applies to the grid as it stands.
 */
object LogicalSolver {

    private const val ALL_DIGITS = 0b11_1111_1110

    /**
     * The outcome of a logical solve.
     *
     * [solved] false means the solver ran out of techniques, not that the puzzle is unsolvable — a
     * puzzle may still have a unique solution reachable only by guessing or by a technique this
     * solver does not know.
     */
    data class Result(
        val solved: Boolean,
        val board: SudokuBoard,
        val techniqueUsage: Map<SolvingTechnique, Int>,
    ) {
        /** The hardest technique the solve actually needed, or `null` if nothing was needed. */
        val hardestTechnique: SolvingTechnique? = techniqueUsage.keys.maxByOrNull { it.rank }
    }

    /**
     * Solves [board] using only [allowed].
     *
     * Restricting the technique set is how the generator asks "is this puzzle within reach of an
     * easy player?" — the answer is whether the restricted solve finishes.
     */
    fun solve(
        board: SudokuBoard,
        allowed: Set<SolvingTechnique> = SolvingTechnique.ALL,
    ): Result {
        val grid = Grid.from(board)
            ?: return Result(solved = false, board = board, techniqueUsage = emptyMap())
        val usage = mutableMapOf<SolvingTechnique, Int>()

        while (!grid.isComplete) {
            val applied = grid.applyCheapestTechnique(allowed) ?: break
            usage[applied] = (usage[applied] ?: 0) + 1
            if (grid.contradicted) {
                return Result(solved = false, board = grid.toBoard(), techniqueUsage = usage)
            }
        }

        return Result(solved = grid.isComplete, board = grid.toBoard(), techniqueUsage = usage)
    }

    /** Grid plus per-cell candidate bitmasks, mutated in place as techniques fire. */
    private class Grid(val cells: IntArray, val candidates: IntArray) {

        var contradicted = false
            private set

        val isComplete: Boolean get() = cells.none { it == Sudoku.EMPTY }

        fun toBoard(): SudokuBoard = SudokuBoard.of(cells)

        fun candidateMask(cell: Int): Int = candidates[cell]

        fun place(cell: Int, digit: Int) {
            cells[cell] = digit
            candidates[cell] = 0
            val bit = 1 shl digit
            for (peer in Sudoku.peers[cell]) {
                if (cells[peer] == digit) contradicted = true
                candidates[peer] = candidates[peer] and bit.inv()
                if (cells[peer] == Sudoku.EMPTY && candidates[peer] == 0) contradicted = true
            }
        }

        /** Removes [mask]'s digits from [cell]; true when something was actually removed. */
        fun eliminate(cell: Int, mask: Int): Boolean {
            if (cells[cell] != Sudoku.EMPTY) return false
            val removed = candidates[cell] and mask
            if (removed == 0) return false
            candidates[cell] = candidates[cell] and removed.inv()
            if (candidates[cell] == 0) contradicted = true
            return true
        }

        /**
         * Runs the allowed techniques from cheapest to dearest and returns the first that changed
         * the grid, or `null` when none did.
         */
        fun applyCheapestTechnique(allowed: Set<SolvingTechnique>): SolvingTechnique? {
            for (technique in SolvingTechnique.entries) {
                if (technique !in allowed) continue
                val progressed = when (technique) {
                    SolvingTechnique.NAKED_SINGLE -> nakedSingle()
                    SolvingTechnique.HIDDEN_SINGLE -> hiddenSingle()
                    SolvingTechnique.LOCKED_CANDIDATES -> lockedCandidates()
                    SolvingTechnique.NAKED_PAIR -> nakedSubset(size = 2)
                    SolvingTechnique.HIDDEN_PAIR -> hiddenSubset(size = 2)
                    SolvingTechnique.NAKED_TRIPLE -> nakedSubset(size = 3)
                    SolvingTechnique.HIDDEN_TRIPLE -> hiddenSubset(size = 3)
                    SolvingTechnique.X_WING -> fish(size = 2)
                    SolvingTechnique.XY_WING -> xyWing()
                    SolvingTechnique.SWORDFISH -> fish(size = 3)
                }
                if (progressed) return technique
            }
            return null
        }

        private fun nakedSingle(): Boolean {
            for (cell in 0 until Sudoku.CELL_COUNT) {
                if (cells[cell] != Sudoku.EMPTY) continue
                val mask = candidates[cell]
                if (Integer.bitCount(mask) == 1) {
                    place(cell, Integer.numberOfTrailingZeros(mask))
                    return true
                }
            }
            return false
        }

        private fun hiddenSingle(): Boolean {
            for (unit in Sudoku.units) {
                for (digit in Sudoku.DIGITS) {
                    val bit = 1 shl digit
                    var target = -1
                    var count = 0
                    var alreadyPlaced = false
                    for (cell in unit) {
                        if (cells[cell] == digit) {
                            alreadyPlaced = true
                            break
                        }
                        if (candidates[cell] and bit != 0) {
                            target = cell
                            count++
                        }
                    }
                    if (!alreadyPlaced && count == 1) {
                        place(target, digit)
                        return true
                    }
                }
            }
            return false
        }

        /**
         * Pointing and claiming. For each block/line pair, if all of a digit's candidates in one of
         * them also lie in the other, the digit can go from the rest of the other.
         */
        private fun lockedCandidates(): Boolean {
            for (block in Sudoku.blocks) {
                for (digit in Sudoku.DIGITS) {
                    val cellsWithDigit = block.filter { candidates[it] and (1 shl digit) != 0 }
                    if (cellsWithDigit.size < 2) continue

                    val rows = cellsWithDigit.map(Sudoku::rowOf).distinct()
                    if (rows.size == 1) {
                        val line = Sudoku.rows[rows.single()]
                        if (eliminateFromLine(line, block, digit)) return true
                    }
                    val columns = cellsWithDigit.map(Sudoku::columnOf).distinct()
                    if (columns.size == 1) {
                        val line = Sudoku.columns[columns.single()]
                        if (eliminateFromLine(line, block, digit)) return true
                    }
                }
            }

            for (line in Sudoku.rows + Sudoku.columns) {
                for (digit in Sudoku.DIGITS) {
                    val cellsWithDigit = line.filter { candidates[it] and (1 shl digit) != 0 }
                    if (cellsWithDigit.size < 2) continue
                    val blocks = cellsWithDigit.map(Sudoku::blockOf).distinct()
                    if (blocks.size != 1) continue
                    val block = Sudoku.blocks[blocks.single()]
                    if (eliminateFromLine(block, line, digit)) return true
                }
            }
            return false
        }

        /** Strikes [digit] from every cell of [from] that is not also in [keep]. */
        private fun eliminateFromLine(from: IntArray, keep: IntArray, digit: Int): Boolean {
            var changed = false
            for (cell in from) {
                if (cell in keep) continue
                if (eliminate(cell, 1 shl digit)) changed = true
            }
            return changed
        }

        /**
         * Naked pair/triple: [size] cells in a unit whose candidates union to exactly [size] digits.
         * Those digits are then used up by those cells and leave the rest of the unit.
         */
        private fun nakedSubset(size: Int): Boolean {
            for (unit in Sudoku.units) {
                // With no more than `size` unsolved cells the subset covers the whole unit and
                // there is nothing left to eliminate from.
                if (unit.count { cells[it] == Sudoku.EMPTY } <= size) continue

                val open = unit.filter {
                    cells[it] == Sudoku.EMPTY && Integer.bitCount(candidates[it]) in 2..size
                }
                if (open.size < size) continue

                for (combination in open.combinations(size)) {
                    var union = 0
                    for (cell in combination) union = union or candidates[cell]
                    if (Integer.bitCount(union) != size) continue

                    var changed = false
                    for (cell in unit) {
                        if (cell in combination) continue
                        if (eliminate(cell, union)) changed = true
                    }
                    if (changed) return true
                }
            }
            return false
        }

        /**
         * Hidden pair/triple: [size] digits in a unit that can only go in the same [size] cells.
         * Everything else leaves those cells.
         */
        private fun hiddenSubset(size: Int): Boolean {
            for (unit in Sudoku.units) {
                val placements = Sudoku.DIGITS.associateWith { digit ->
                    unit.filter { candidates[it] and (1 shl digit) != 0 }
                }.filterValues { it.size in 2..size }
                if (placements.size < size) continue

                for (digits in placements.keys.toList().combinations(size)) {
                    val covered = mutableSetOf<Int>()
                    for (digit in digits) covered += placements.getValue(digit)
                    if (covered.size != size) continue

                    var keepMask = 0
                    for (digit in digits) keepMask = keepMask or (1 shl digit)

                    var changed = false
                    for (cell in covered) {
                        if (eliminate(cell, candidates[cell] and keepMask.inv())) changed = true
                    }
                    if (changed) return true
                }
            }
            return false
        }

        /**
         * Basic fish: X-Wing at [size] 2, Swordfish at 3.
         *
         * When a digit's candidates in [size] rows all fall inside the same [size] columns, those
         * rows must consume the digit in those columns, so it can go from the columns' other cells.
         * Runs once per orientation.
         */
        private fun fish(size: Int): Boolean {
            for (digit in Sudoku.DIGITS) {
                if (fishInOrientation(digit, size, Sudoku.rows, Sudoku.columns, Sudoku::columnOf)) {
                    return true
                }
                if (fishInOrientation(digit, size, Sudoku.columns, Sudoku.rows, Sudoku::rowOf)) {
                    return true
                }
            }
            return false
        }

        private fun fishInOrientation(
            digit: Int,
            size: Int,
            bases: Array<IntArray>,
            covers: Array<IntArray>,
            coverIndexOf: (Int) -> Int,
        ): Boolean {
            val bit = 1 shl digit
            val usableBases = bases.indices.filter { base ->
                val count = bases[base].count { candidates[it] and bit != 0 }
                count in 2..size
            }
            if (usableBases.size < size) return false

            for (combination in usableBases.combinations(size)) {
                val coverIndices = mutableSetOf<Int>()
                val baseCells = mutableSetOf<Int>()
                for (base in combination) {
                    for (cell in bases[base]) {
                        if (candidates[cell] and bit == 0) continue
                        coverIndices += coverIndexOf(cell)
                        baseCells += cell
                    }
                }
                if (coverIndices.size != size) continue

                var changed = false
                for (cover in coverIndices) {
                    for (cell in covers[cover]) {
                        // Cells of the base units carry the pattern itself; only the rest of the
                        // cover units lose the digit.
                        if (cell in baseCells) continue
                        if (eliminate(cell, bit)) changed = true
                    }
                }
                if (changed) return true
            }
            return false
        }

        /**
         * XY-Wing: a bivalue pivot `{x,y}` sees a bivalue `{x,z}` and a bivalue `{y,z}`. Whichever
         * digit the pivot takes, one pincer becomes `z`, so `z` leaves every cell both pincers see.
         */
        private fun xyWing(): Boolean {
            val bivalue = (0 until Sudoku.CELL_COUNT).filter {
                cells[it] == Sudoku.EMPTY && Integer.bitCount(candidates[it]) == 2
            }

            for (pivot in bivalue) {
                val pivotDigits = candidates[pivot].digits()
                val (x, y) = pivotDigits
                val pincers = Sudoku.peers[pivot].filter { it in bivalue }

                for (first in pincers) {
                    for (second in pincers) {
                        if (second <= first) continue
                        val z = sharedThirdDigit(candidates[first], candidates[second], x, y)
                            ?: continue

                        val bit = 1 shl z
                        var changed = false
                        for (cell in Sudoku.peers[first]) {
                            if (cell == pivot || cell == second) continue
                            if (cell !in Sudoku.peers[second]) continue
                            if (eliminate(cell, bit)) changed = true
                        }
                        if (changed) return true
                    }
                }
            }
            return false
        }

        /**
         * The digit `z` such that the two pincer masks are exactly `{x,z}` and `{y,z}` in some
         * order, or `null` when they do not form a wing with this pivot.
         */
        private fun sharedThirdDigit(firstMask: Int, secondMask: Int, x: Int, y: Int): Int? {
            val xBit = 1 shl x
            val yBit = 1 shl y
            val pivotMask = xBit or yBit

            val firstExtra = firstMask and pivotMask.inv()
            val secondExtra = secondMask and pivotMask.inv()
            if (Integer.bitCount(firstExtra) != 1 || firstExtra != secondExtra) return null

            // One pincer must hold x and the other y; two `{x,z}` pincers prove nothing.
            val firstShared = firstMask and pivotMask
            val secondShared = secondMask and pivotMask
            if (firstShared == secondShared) return null
            if (Integer.bitCount(firstShared) != 1 || Integer.bitCount(secondShared) != 1) return null

            return Integer.numberOfTrailingZeros(firstExtra)
        }

        companion object {
            /** `null` when [board] already breaks a rule. */
            fun from(board: SudokuBoard): Grid? {
                val grid = Grid(
                    cells = IntArray(Sudoku.CELL_COUNT),
                    candidates = IntArray(Sudoku.CELL_COUNT) { ALL_DIGITS },
                )
                for (cell in 0 until Sudoku.CELL_COUNT) {
                    val digit = board[cell]
                    if (digit == Sudoku.EMPTY) continue
                    if (grid.candidates[cell] and (1 shl digit) == 0) return null
                    grid.place(cell, digit)
                    if (grid.contradicted) return null
                }
                return grid
            }
        }
    }
}

/** The digits set in a candidate bitmask, ascending. */
private fun Int.digits(): List<Int> = Sudoku.DIGITS.filter { this and (1 shl it) != 0 }

/** Every [size]-element combination of this list, in index order. */
private fun <T> List<T>.combinations(size: Int): Sequence<List<T>> = sequence {
    if (size > this@combinations.size) return@sequence
    val positions = IntArray(size) { it }
    while (true) {
        yield(positions.map { this@combinations[it] })
        var cursor = size - 1
        while (cursor >= 0 && positions[cursor] == this@combinations.size - size + cursor) cursor--
        if (cursor < 0) return@sequence
        positions[cursor]++
        for (next in cursor + 1 until size) positions[next] = positions[next - 1] + 1
    }
}
