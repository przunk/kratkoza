package com.przunk.kratkoza.domain.sudoku

/**
 * Hand-checked boards shared by the domain tests.
 *
 * Each is one 81-character line, `.` for empty. [SudokuBoard.parse] also accepts them split across
 * lines, which one board test relies on.
 */
object SudokuFixtures {

    /** A 30-clue board with a unique solution, reachable with singles alone. */
    const val EASY_GIVENS =
        "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"

    const val EASY_SOLUTION =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    /**
     * [EASY_GIVENS] with the clue at index 20 removed, which leaves the board with more than one
     * solution. Checked by hand; used to verify that uniqueness is genuinely tested rather than
     * assumed.
     */
    const val EASY_WITH_TWO_SOLUTIONS =
        "53..7....6..195....9.....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"

    /** Two 5s in the top row, so no solution exists. */
    const val CONTRADICTORY =
        "5.5.............................................................................."

    /** Nothing given at all — the extreme case for solution counting. */
    const val EMPTY = "................................................................................."
}
