package com.przunk.kratkoza.domain.sudoku

/**
 * Geometry of a classic 9x9 sudoku grid, precomputed once.
 *
 * Cells are addressed by a single index `0..80`, laid out row by row. The solver, the generator and
 * the candidate logic all walk peers and units in tight loops, so the lookup tables are built here
 * rather than recomputed from row/column arithmetic on every step.
 */
object Sudoku {
    const val SIZE = 9
    const val BLOCK_SIZE = 3
    const val CELL_COUNT = SIZE * SIZE

    /** Value stored in a cell that holds no digit. */
    const val EMPTY = 0

    /** Every digit that may be placed in a cell. */
    val DIGITS: IntRange = 1..SIZE

    fun index(row: Int, column: Int): Int = row * SIZE + column

    fun rowOf(index: Int): Int = index / SIZE

    fun columnOf(index: Int): Int = index % SIZE

    fun blockOf(index: Int): Int =
        (rowOf(index) / BLOCK_SIZE) * BLOCK_SIZE + columnOf(index) / BLOCK_SIZE

    fun isDigit(value: Int): Boolean = value in DIGITS

    fun isCellIndex(index: Int): Boolean = index in 0 until CELL_COUNT

    /** The nine cells of each row, indexed by row number. */
    val rows: Array<IntArray> = Array(SIZE) { row ->
        IntArray(SIZE) { column -> index(row, column) }
    }

    /** The nine cells of each column, indexed by column number. */
    val columns: Array<IntArray> = Array(SIZE) { column ->
        IntArray(SIZE) { row -> index(row, column) }
    }

    /** The nine cells of each 3x3 block, indexed by block number (left to right, top to bottom). */
    val blocks: Array<IntArray> = Array(SIZE) { block ->
        val firstRow = (block / BLOCK_SIZE) * BLOCK_SIZE
        val firstColumn = (block % BLOCK_SIZE) * BLOCK_SIZE
        IntArray(SIZE) { offset ->
            index(firstRow + offset / BLOCK_SIZE, firstColumn + offset % BLOCK_SIZE)
        }
    }

    /** All 27 units — nine rows, then nine columns, then nine blocks. */
    val units: Array<IntArray> = rows + columns + blocks

    /**
     * The 20 cells that share a row, column or block with a given cell. A digit placed in a cell can
     * only ever conflict with, or be eliminated from, these.
     */
    val peers: Array<IntArray> = Array(CELL_COUNT) { cell ->
        val row = rowOf(cell)
        val column = columnOf(cell)
        val block = blockOf(cell)
        val collected = LinkedHashSet<Int>(20)
        collected += rows[row].asList()
        collected += columns[column].asList()
        collected += blocks[block].asList()
        collected -= cell
        collected.toIntArray()
    }

    /** The three units (row, column, block) each cell belongs to. */
    val unitsOf: Array<Array<IntArray>> = Array(CELL_COUNT) { cell ->
        arrayOf(rows[rowOf(cell)], columns[columnOf(cell)], blocks[blockOf(cell)])
    }
}
