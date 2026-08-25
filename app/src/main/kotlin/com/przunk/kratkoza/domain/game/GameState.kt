package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.model.Puzzle
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuValidator

/**
 * Everything about a game in progress that belongs to the board rather than to the screen.
 *
 * Deliberately outside this type: the selected cell, whether candidate mode is on, and the clock.
 * The first two are view state, and the clock ticks once a second — folding it in here would make
 * every tick look like a board change to the UI and to the automatic save.
 *
 * [com.przunk.kratkoza.data.repository.SavedGame] pairs this with the timing and bookkeeping the
 * save needs.
 */
data class GameState(
    val puzzle: Puzzle,
    /** Givens plus whatever the player has entered. */
    val board: SudokuBoard,
    val candidates: CandidateGrid,
    /**
     * How many wrong digits have been committed (project.md section 16). Never decreases: correcting
     * a cell or undoing the move does not undo the mistake.
     */
    val mistakeCount: Int,
    /** Most recent move last. */
    val undoStack: List<Move>,
) {
    /**
     * True when the board matches the puzzle's solution.
     *
     * Compared against the solution rather than merely checked for consistency: project.md
     * section 16 requires that a game only counts as finished when it is *correctly* solved.
     */
    val isSolved: Boolean get() = board == puzzle.solution

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Cells holding a digit that disagrees with the solution (project.md section 16). */
    val incorrectCells: Set<Int>
        get() = (0 until Sudoku.CELL_COUNT).filterTo(mutableSetOf()) { cell ->
            !board.isEmpty(cell) && board[cell] != puzzle.solution[cell]
        }

    /** Cells that repeat a digit inside a row, column or block. */
    val conflictingCells: Set<Int> get() = SudokuValidator.conflictingCells(board)

    /**
     * How many of [digit] are still missing, `0` once all nine sit where they belong.
     *
     * Counts *correct* placements only, which is what project.md section 19 asks for: "how many
     * correct occurrences of the digit remain". A wrong copy of a digit is not one of its nine, and
     * counting it would say the digit was finished while a cell still needed it — and then, since
     * [isDigitComplete] decides whether the key can be pressed, lock the player out of fixing it.
     */
    fun remainingCountOf(digit: Int): Int {
        var placed = 0
        for (cell in 0 until Sudoku.CELL_COUNT) {
            if (board[cell] == digit && puzzle.solution[cell] == digit) placed++
        }
        return (Sudoku.SIZE - placed).coerceAtLeast(0)
    }

    /**
     * True when every nine of [digit] is correctly placed, so it can go nowhere else.
     *
     * Entering it again could only ever be a mistake, which is why the number pad refuses it.
     */
    fun isDigitComplete(digit: Int): Boolean = remainingCountOf(digit) == 0

    fun isGiven(index: Int): Boolean = puzzle.isGiven(index)

    companion object {
        /** A fresh game: the givens on the board, no marks, no mistakes, nothing to undo. */
        fun newGame(puzzle: Puzzle): GameState = GameState(
            puzzle = puzzle,
            board = puzzle.givens,
            candidates = CandidateGrid.EMPTY,
            mistakeCount = 0,
            undoStack = emptyList(),
        )
    }
}
