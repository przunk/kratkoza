package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.model.Puzzle
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures

/** A hand-checked puzzle for the game tests, so they never depend on the generator's randomness. */
object GameFixtures {

    val PUZZLE = Puzzle(
        id = "test-puzzle",
        generatorVersion = 1,
        difficulty = Difficulty.NORMAL,
        gameType = GameType.SOLO,
        givens = SudokuBoard.parse(SudokuFixtures.EASY_GIVENS),
        solution = SudokuBoard.parse(SudokuFixtures.EASY_SOLUTION),
        seed = null,
    )

    fun newGame(): GameState = GameState.newGame(PUZZLE)

    /** An empty cell of [PUZZLE], with the digit that belongs there. */
    val EMPTY_CELL: Int = (0 until 81).first { PUZZLE.givens.isEmpty(it) }
    val EMPTY_CELL_SOLUTION: Int = PUZZLE.solution[EMPTY_CELL]

    /** A digit that does not belong in [EMPTY_CELL]. */
    val EMPTY_CELL_WRONG_DIGIT: Int = (1..9).first { it != EMPTY_CELL_SOLUTION }

    /** A cell holding a given, which the player may never change. */
    val GIVEN_CELL: Int = (0 until 81).first { !PUZZLE.givens.isEmpty(it) }
}
