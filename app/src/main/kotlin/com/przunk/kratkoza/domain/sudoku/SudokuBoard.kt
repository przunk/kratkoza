package com.przunk.kratkoza.domain.sudoku

/**
 * An immutable 9x9 grid of digits, `0` meaning empty.
 *
 * Used for every kind of grid the app handles — the givens, the solution and the player's current
 * state — because they differ only in how they are interpreted, not in shape. Immutability is what
 * makes undo (see `MoveHistory`) and state snapshots cheap and safe to hand to the UI.
 */
class SudokuBoard private constructor(private val cells: IntArray) {

    init {
        require(cells.size == Sudoku.CELL_COUNT) {
            "A sudoku board has ${Sudoku.CELL_COUNT} cells, got ${cells.size}"
        }
    }

    operator fun get(index: Int): Int = cells[index]

    operator fun get(row: Int, column: Int): Int = cells[Sudoku.index(row, column)]

    fun isEmpty(index: Int): Boolean = cells[index] == Sudoku.EMPTY

    /** Number of cells holding a digit. */
    val filledCount: Int get() = cells.count { it != Sudoku.EMPTY }

    val isComplete: Boolean get() = cells.none { it == Sudoku.EMPTY }

    /** This board with [value] at [index]; `0` clears the cell. Returns `this` when nothing changes. */
    fun withValue(index: Int, value: Int): SudokuBoard {
        require(Sudoku.isCellIndex(index)) { "Cell index out of range: $index" }
        require(value == Sudoku.EMPTY || Sudoku.isDigit(value)) { "Not a sudoku value: $value" }
        if (cells[index] == value) return this
        val updated = cells.copyOf()
        updated[index] = value
        return SudokuBoard(updated)
    }

    /** A mutable copy for algorithms that need to write in place; never aliases this board. */
    fun toIntArray(): IntArray = cells.copyOf()

    /**
     * The 81 digits as text, `.` for empty. This is the on-disk and cross-process representation:
     * it is compact, human-readable in a database browser, and stable across app versions.
     */
    fun toStorageString(): String = buildString(Sudoku.CELL_COUNT) {
        for (value in cells) append(if (value == Sudoku.EMPTY) '.' else '0' + value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SudokuBoard && cells.contentEquals(other.cells))

    override fun hashCode(): Int = cells.contentHashCode()

    override fun toString(): String = toStorageString()

    companion object {
        val EMPTY: SudokuBoard = SudokuBoard(IntArray(Sudoku.CELL_COUNT))

        /** Takes a defensive copy, so the caller may keep mutating [values]. */
        fun of(values: IntArray): SudokuBoard = SudokuBoard(values.copyOf())

        fun of(values: List<Int>): SudokuBoard = SudokuBoard(values.toIntArray())

        /**
         * Parses [toStorageString]. Accepts `.`, `0` and space as empty so hand-written fixtures in
         * tests stay readable; any other character is rejected.
         */
        fun parse(text: String): SudokuBoard {
            val compact = text.filterNot { it == '\n' || it == '\r' }
            require(compact.length == Sudoku.CELL_COUNT) {
                "A sudoku board needs ${Sudoku.CELL_COUNT} characters, got ${compact.length}"
            }
            val values = IntArray(Sudoku.CELL_COUNT) { position ->
                when (val character = compact[position]) {
                    '.', '0', ' ' -> Sudoku.EMPTY
                    in '1'..'9' -> character - '0'
                    else -> throw IllegalArgumentException(
                        "Unexpected character '$character' at position $position",
                    )
                }
            }
            return SudokuBoard(values)
        }
    }
}
