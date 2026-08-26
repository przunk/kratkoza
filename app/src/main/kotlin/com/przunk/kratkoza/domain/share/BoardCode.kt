package com.przunk.kratkoza.domain.share

import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import java.math.BigInteger

/**
 * Turns a board into a short text code and back, so one player can hand a puzzle to another.
 *
 * **The code carries the layout, not the seed.** A seed would be shorter — the generator is
 * deterministic, so `seed + difficulty` reproduces a board exactly — but it would also stop working
 * the day `SudokuGenerator.GENERATOR_VERSION` changes, and that constant exists precisely because
 * it will. A code that quietly yields a *different* board after an update is worse than a long one,
 * and the layout also lets a board be shared that this generator never produced.
 *
 * **The code does not carry the difficulty either.** It is recomputed on the receiving side from the
 * board itself, so the label cannot lie about what it is handing over. Both players running the same
 * app grade it identically; a grading change between versions would shift both, which is the honest
 * outcome.
 *
 * Format: `KZ1-` followed by base-36 digits. What is encoded, as one decimal number:
 *
 *     1  <generator version, 2 digits>  <81 board digits>  <checksum, 2 digits>
 *     ^ sentinel, so leading zeros in the board survive the conversion
 *
 * Base 36 rather than a hand-rolled base 32: `BigInteger` already speaks it, and the code is meant
 * to be copied and pasted rather than typed from a screen across the room.
 */
object BoardCode {

    private const val PREFIX = "KZ1-"
    private const val RADIX = 36
    private const val VERSION_DIGITS = 2
    private const val CHECKSUM_MODULUS = 97
    private const val CHECKSUM_DIGITS = 2

    /** Groups of this many characters, joined by dashes, so a long code stays readable. */
    private const val GROUP_SIZE = 5

    /** The code for [givens]. [generatorVersion] travels so the puzzle keeps its identity. */
    fun encode(givens: SudokuBoard, generatorVersion: Int): String {
        require(generatorVersion in 0 until 100) {
            "Generator version must fit in $VERSION_DIGITS digits, got $generatorVersion"
        }

        val board = digitsOf(givens)
        val payload = buildString {
            append('1')
            append(generatorVersion.toString().padStart(VERSION_DIGITS, '0'))
            append(board)
            append(checksumOf(generatorVersion, board).toString().padStart(CHECKSUM_DIGITS, '0'))
        }

        val text = BigInteger(payload).toString(RADIX).uppercase()
        return PREFIX + text.chunked(GROUP_SIZE).joinToString("-")
    }

    /**
     * Reads a code back, or explains why it cannot.
     *
     * Only shape is checked here — that the text is a code of ours and survived the trip intact.
     * Whether the board is a *usable puzzle* is a separate question, answered by whoever imports it,
     * because it needs a solver.
     */
    fun decode(code: String): Result {
        // People paste codes with spaces, line breaks and the grouping dashes still in them.
        val cleaned = code.trim().uppercase().filterNot { it == '-' || it.isWhitespace() }
        val body = cleaned.removePrefix(PREFIX.trimEnd('-'))
        if (body == cleaned) return Result.NotABoardCode
        if (body.isEmpty()) return Result.Damaged

        val number = try {
            BigInteger(body, RADIX)
        } catch (_: NumberFormatException) {
            return Result.Damaged
        }

        val payload = number.toString()
        val expectedLength = 1 + VERSION_DIGITS + Sudoku.CELL_COUNT + CHECKSUM_DIGITS
        if (payload.length != expectedLength || payload[0] != '1') return Result.Damaged

        val generatorVersion = payload.substring(1, 1 + VERSION_DIGITS).toInt()
        val board = payload.substring(1 + VERSION_DIGITS, 1 + VERSION_DIGITS + Sudoku.CELL_COUNT)
        val checksum = payload.substring(payload.length - CHECKSUM_DIGITS).toInt()

        // Catches a code that was truncated or mistyped, so the player is told the code is broken
        // rather than handed some other board that happens to parse.
        if (checksum != checksumOf(generatorVersion, board)) return Result.Damaged

        val givens = try {
            SudokuBoard.parse(board)
        } catch (_: IllegalArgumentException) {
            return Result.Damaged
        }

        return Result.Decoded(givens = givens, generatorVersion = generatorVersion)
    }

    private fun digitsOf(board: SudokuBoard): String = buildString(Sudoku.CELL_COUNT) {
        for (cell in 0 until Sudoku.CELL_COUNT) append('0' + board[cell])
    }

    /** Position-weighted, so swapping two digits changes it — a plain sum would not notice. */
    private fun checksumOf(generatorVersion: Int, board: String): Int {
        var total = generatorVersion
        for (index in board.indices) {
            total += (board[index] - '0') * (index + 1)
        }
        return total % CHECKSUM_MODULUS
    }

    sealed interface Result {
        data class Decoded(val givens: SudokuBoard, val generatorVersion: Int) : Result

        /** The text is not one of our codes at all — a link, a word, an empty field. */
        data object NotABoardCode : Result

        /** It is one of ours, but it did not survive the trip. */
        data object Damaged : Result
    }
}
