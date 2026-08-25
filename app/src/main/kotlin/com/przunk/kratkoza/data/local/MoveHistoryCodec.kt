package com.przunk.kratkoza.data.local

import com.przunk.kratkoza.domain.game.Move
import com.przunk.kratkoza.domain.sudoku.Sudoku

/**
 * Serialises the undo stack for the save (project.md section 22).
 *
 * A hand-written codec rather than a JSON library: the shape is three integers and a small map of
 * integers, the app has no other use for JSON, and technical_requirements.md section 9 asks for a
 * dependency to earn its place. It is also fully covered by tests, which is where the confidence
 * that would otherwise come from a library has to come from.
 *
 * Format, with a move per `;`:
 *
 *     index,previousValue,newValue,cell:mask.cell:mask
 *
 * The trailing candidate section is omitted when the move changed no pencil marks.
 */
object MoveHistoryCodec {

    private const val MOVE_SEPARATOR = ';'
    private const val FIELD_SEPARATOR = ','
    private const val CANDIDATE_SEPARATOR = '.'
    private const val CELL_MASK_SEPARATOR = ':'

    fun encode(moves: List<Move>): String = moves.joinToString(MOVE_SEPARATOR.toString()) { move ->
        buildString {
            append(move.index)
            append(FIELD_SEPARATOR)
            append(move.previousValue)
            append(FIELD_SEPARATOR)
            append(move.newValue)
            if (move.previousCandidates.isNotEmpty()) {
                append(FIELD_SEPARATOR)
                append(
                    move.previousCandidates.entries
                        .sortedBy { it.key }
                        .joinToString(CANDIDATE_SEPARATOR.toString()) { (cell, digits) ->
                            "$cell$CELL_MASK_SEPARATOR${maskOf(digits)}"
                        },
                )
            }
        }
    }

    /**
     * Rebuilds the stack, or throws [IllegalArgumentException] on anything it does not recognise.
     *
     * Failing loudly is deliberate. A save whose undo stack cannot be read is a corrupt save, and
     * the caller has to decide what to do about it; quietly returning an empty stack would hand the
     * player a game whose undo silently does nothing.
     */
    fun decode(text: String): List<Move> {
        if (text.isEmpty()) return emptyList()
        return text.split(MOVE_SEPARATOR).map(::decodeMove)
    }

    private fun decodeMove(text: String): Move {
        val fields = text.split(FIELD_SEPARATOR)
        require(fields.size == 3 || fields.size == 4) { "Malformed move: $text" }

        return Move(
            index = cellIndex(fields[0], text),
            previousValue = cellValue(fields[1], text),
            newValue = cellValue(fields[2], text),
            previousCandidates = if (fields.size == 4) decodeCandidates(fields[3], text) else emptyMap(),
        )
    }

    private fun decodeCandidates(text: String, move: String): Map<Int, Set<Int>> =
        text.split(CANDIDATE_SEPARATOR).associate { entry ->
            val parts = entry.split(CELL_MASK_SEPARATOR)
            require(parts.size == 2) { "Malformed candidate entry in move: $move" }
            val mask = parts[1].toIntOrNull()
            requireNotNull(mask) { "Malformed candidate mask in move: $move" }
            require(mask and ALL_DIGITS.inv() == 0) { "Candidate mask out of range in move: $move" }
            cellIndex(parts[0], move) to digitsOf(mask)
        }

    private fun cellIndex(text: String, move: String): Int {
        val index = text.toIntOrNull()
        require(index != null && Sudoku.isCellIndex(index)) { "Bad cell index in move: $move" }
        return index
    }

    private fun cellValue(text: String, move: String): Int {
        val value = text.toIntOrNull()
        require(value != null && (value == Sudoku.EMPTY || Sudoku.isDigit(value))) {
            "Bad cell value in move: $move"
        }
        return value
    }

    private const val ALL_DIGITS = 0b11_1111_1110

    private fun maskOf(digits: Set<Int>): Int {
        var mask = 0
        for (digit in digits) mask = mask or (1 shl digit)
        return mask
    }

    private fun digitsOf(mask: Int): Set<Int> {
        if (mask == 0) return emptySet()
        return Sudoku.DIGITS.filterTo(LinkedHashSet()) { mask and (1 shl it) != 0 }
    }
}
