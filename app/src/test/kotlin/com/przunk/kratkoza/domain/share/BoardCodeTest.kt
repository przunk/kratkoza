package com.przunk.kratkoza.domain.share

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardCodeTest {

    private val givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS)

    private fun decoded(code: String): BoardCode.Result.Decoded {
        val result = BoardCode.decode(code)
        assertTrue("expected a decodable code, got $result", result is BoardCode.Result.Decoded)
        return result as BoardCode.Result.Decoded
    }

    @Test
    fun `a board survives the round trip`() {
        val result = decoded(BoardCode.encode(givens, generatorVersion = 1))

        assertEquals(givens, result.givens)
        assertEquals(1, result.generatorVersion)
    }

    @Test
    fun `every generated board survives the round trip`() {
        val generator = SudokuGenerator()
        val random = Random(4242)

        for (difficulty in Difficulty.entries) {
            val puzzle = generator.generate(difficulty, random = random)
            val code = BoardCode.encode(puzzle.givens, puzzle.generatorVersion)

            assertEquals("$difficulty puzzle ${puzzle.id}", puzzle.givens, decoded(code).givens)
        }
    }

    @Test
    fun `a board that begins with an empty cell survives`() {
        // The sentinel digit exists for this: without it the leading zero is lost in the conversion
        // and every cell shifts by one.
        val leadingGap = givens.withValue(0, Sudoku.EMPTY)
        assertTrue(leadingGap.isEmpty(0))

        assertEquals(leadingGap, decoded(BoardCode.encode(leadingGap, 1)).givens)
    }

    @Test
    fun `an empty board survives`() {
        assertEquals(SudokuBoard.EMPTY, decoded(BoardCode.encode(SudokuBoard.EMPTY, 1)).givens)
    }

    @Test
    fun `a full board survives`() {
        val solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION)
        assertEquals(solution, decoded(BoardCode.encode(solution, 1)).givens)
    }

    @Test
    fun `the code is grouped and prefixed so it is recognisable`() {
        val code = BoardCode.encode(givens, 1)

        assertTrue("should announce itself: $code", code.startsWith("KZ1-"))
        assertTrue("should be broken into groups: $code", code.count { it == '-' } > 1)
    }

    @Test
    fun `codes pasted with stray whitespace, line breaks or lower case still work`() {
        val code = BoardCode.encode(givens, 1)
        val mangled = "  " + code.lowercase().replace("-", " -\n") + "  "

        assertEquals(givens, decoded(mangled).givens)
    }

    @Test
    fun `the grouping dashes are decoration and may be dropped`() {
        val code = BoardCode.encode(givens, 1)
        val withoutDashes = "KZ1-" + code.removePrefix("KZ1-").replace("-", "")

        assertEquals(givens, decoded(withoutDashes).givens)
    }

    @Test
    fun `different boards get different codes`() {
        val other = givens.withValue(2, 4)
        assertNotEquals(BoardCode.encode(givens, 1), BoardCode.encode(other, 1))
    }

    @Test
    fun `text that is not a code at all is reported as such`() {
        for (text in listOf("", "   ", "hello", "https://example.com", "1234567890")) {
            assertEquals(
                "for input '$text'",
                BoardCode.Result.NotABoardCode,
                BoardCode.decode(text),
            )
        }
    }

    @Test
    fun `a truncated code is reported as damaged, never as another board`() {
        val code = BoardCode.encode(givens, 1)

        assertEquals(BoardCode.Result.Damaged, BoardCode.decode(code.dropLast(3)))
        assertEquals(BoardCode.Result.Damaged, BoardCode.decode("KZ1-"))
    }

    @Test
    fun `a single mistyped character is caught rather than yielding a different board`() {
        // The point of the checksum. Silently handing over the wrong board would be the worst
        // outcome here, because nothing downstream could tell.
        val code = BoardCode.encode(givens, 1)
        val body = code.removePrefix("KZ1-").replace("-", "")

        var caught = 0
        var slipped = 0
        for (index in body.indices) {
            val original = body[index]
            val replacement = if (original == 'A') 'B' else 'A'
            val broken = "KZ1-" + body.replaceRange(index, index + 1, replacement.toString())

            when (val result = BoardCode.decode(broken)) {
                is BoardCode.Result.Decoded -> {
                    slipped++
                    assertNotEquals("a corrupted code must not decode to the original board", givens, result.givens)
                }
                else -> caught++
            }
        }

        assertTrue("the checksum should catch most single-character damage", caught > slipped)
    }

    @Test
    fun `swapping two characters is caught`() {
        // A plain sum would not notice a transposition; the checksum is position-weighted for this.
        val code = BoardCode.encode(givens, 1)
        val body = code.removePrefix("KZ1-").replace("-", "")
        val swapIndex = body.indices.first { body[it] != body[it + 1] }
        val swapped = body.toCharArray().also {
            val held = it[swapIndex]
            it[swapIndex] = it[swapIndex + 1]
            it[swapIndex + 1] = held
        }.concatToString()

        assertNotEquals(
            "a transposition must not decode to the original board",
            givens,
            (BoardCode.decode("KZ1-$swapped") as? BoardCode.Result.Decoded)?.givens,
        )
    }

    @Test
    fun `the generator version travels with the board`() {
        assertEquals(7, decoded(BoardCode.encode(givens, generatorVersion = 7)).generatorVersion)
        assertEquals(0, decoded(BoardCode.encode(givens, generatorVersion = 0)).generatorVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a generator version too large to encode is refused loudly`() {
        BoardCode.encode(givens, generatorVersion = 100)
    }

    @Test
    fun `the code stays short enough to paste into a message`() {
        val code = BoardCode.encode(SudokuBoard.EMPTY, 1)
        val longest = BoardCode.encode(SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION), 99)

        assertTrue("empty board code was ${code.length} chars", code.length < 80)
        assertTrue("longest code was ${longest.length} chars", longest.length < 80)
    }
}
