package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku

/**
 * The player's pencil marks — one set of digits per cell (project.md section 14).
 *
 * Immutable like [com.przunk.kratkoza.domain.sudoku.SudokuBoard], for the same reason: undo and the
 * automatic save both need to hold on to earlier versions without fear of them changing underneath.
 *
 * Stored internally as one bitmask per cell, which makes union and difference — the operations
 * automatic candidate removal performs on twenty peers at a time — single instructions.
 */
class CandidateGrid private constructor(private val masks: IntArray) {

    init {
        require(masks.size == Sudoku.CELL_COUNT) {
            "A candidate grid has ${Sudoku.CELL_COUNT} cells, got ${masks.size}"
        }
    }

    operator fun get(index: Int): Set<Int> = maskToSet(masks[index])

    fun contains(index: Int, digit: Int): Boolean = masks[index] and (1 shl digit) != 0

    fun isEmpty(index: Int): Boolean = masks[index] == 0

    val hasAny: Boolean get() = masks.any { it != 0 }

    /** Adds [digit] to [index] if absent, removes it if present. */
    fun toggle(index: Int, digit: Int): CandidateGrid {
        require(Sudoku.isDigit(digit)) { "Not a sudoku digit: $digit" }
        return withMask(index, masks[index] xor (1 shl digit))
    }

    fun with(index: Int, digits: Set<Int>): CandidateGrid {
        var mask = 0
        for (digit in digits) {
            require(Sudoku.isDigit(digit)) { "Not a sudoku digit: $digit" }
            mask = mask or (1 shl digit)
        }
        return withMask(index, mask)
    }

    /** Clears every pencil mark in [index]. */
    fun cleared(index: Int): CandidateGrid = withMask(index, 0)

    /**
     * Removes [digit] from every cell in [indices]. Used by automatic candidate removal, which
     * touches up to twenty cells per placed digit and should not allocate a grid for each one.
     */
    fun removeDigitFrom(indices: IntArray, digit: Int): CandidateGrid {
        val bit = 1 shl digit
        var updated: IntArray? = null
        for (index in indices) {
            if (masks[index] and bit == 0) continue
            val target = updated ?: masks.copyOf().also { updated = it }
            target[index] = target[index] and bit.inv()
        }
        return updated?.let(::CandidateGrid) ?: this
    }

    /** The cells whose marks differ between this grid and [other]. */
    fun differingCells(other: CandidateGrid): List<Int> =
        (0 until Sudoku.CELL_COUNT).filter { masks[it] != other.masks[it] }

    private fun withMask(index: Int, mask: Int): CandidateGrid {
        require(Sudoku.isCellIndex(index)) { "Cell index out of range: $index" }
        if (masks[index] == mask) return this
        val updated = masks.copyOf()
        updated[index] = mask
        return CandidateGrid(updated)
    }

    /**
     * Three hex characters per cell, 243 in total.
     *
     * Fixed width so it can be parsed without scanning, and readable enough in a database browser to
     * debug a broken save by eye.
     */
    fun toStorageString(): String = buildString(Sudoku.CELL_COUNT * HEX_PER_CELL) {
        for (mask in masks) append(mask.toString(16).padStart(HEX_PER_CELL, '0'))
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is CandidateGrid && masks.contentEquals(other.masks))

    override fun hashCode(): Int = masks.contentHashCode()

    override fun toString(): String = toStorageString()

    companion object {
        private const val HEX_PER_CELL = 3

        val EMPTY: CandidateGrid = CandidateGrid(IntArray(Sudoku.CELL_COUNT))

        fun of(perCell: List<Set<Int>>): CandidateGrid {
            require(perCell.size == Sudoku.CELL_COUNT) {
                "A candidate grid has ${Sudoku.CELL_COUNT} cells, got ${perCell.size}"
            }
            val masks = IntArray(Sudoku.CELL_COUNT)
            for (index in perCell.indices) {
                for (digit in perCell[index]) {
                    require(Sudoku.isDigit(digit)) { "Not a sudoku digit: $digit" }
                    masks[index] = masks[index] or (1 shl digit)
                }
            }
            return CandidateGrid(masks)
        }

        fun parse(text: String): CandidateGrid {
            require(text.length == Sudoku.CELL_COUNT * HEX_PER_CELL) {
                "A candidate grid needs ${Sudoku.CELL_COUNT * HEX_PER_CELL} characters, got ${text.length}"
            }
            val masks = IntArray(Sudoku.CELL_COUNT) { index ->
                val start = index * HEX_PER_CELL
                val mask = text.substring(start, start + HEX_PER_CELL).toIntOrNull(16)
                requireNotNull(mask) { "Not a hex candidate mask at cell $index" }
                require(mask and ALL_DIGITS.inv() == 0) { "Candidate mask out of range at cell $index" }
                mask
            }
            return CandidateGrid(masks)
        }

        private const val ALL_DIGITS = 0b11_1111_1110

        private fun maskToSet(mask: Int): Set<Int> {
            if (mask == 0) return emptySet()
            val digits = LinkedHashSet<Int>(Integer.bitCount(mask))
            for (digit in Sudoku.DIGITS) {
                if (mask and (1 shl digit) != 0) digits += digit
            }
            return digits
        }
    }
}
