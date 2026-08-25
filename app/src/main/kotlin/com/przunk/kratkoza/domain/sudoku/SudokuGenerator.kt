package com.przunk.kratkoza.domain.sudoku

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.model.Puzzle
import kotlin.random.Random

/**
 * Produces puzzles that satisfy project.md section 5: exactly one solution, solvable by logic alone,
 * and genuinely at the requested difficulty.
 *
 * The shape of the algorithm is the classic one — build a full solution, then remove clues — with
 * three rules on the removal step:
 *
 *  - a clue is only removed while the board still has a unique solution,
 *  - a clue is only removed while [LogicalSolver] can still finish the board *within the requested
 *    level's technique ceiling*, and
 *  - removal stops at the level's [Difficulty.minimumGivens].
 *
 * The second rule is what keeps an easy puzzle easy: removing clues by uniqueness alone drifts
 * toward boards needing advanced techniques, whatever level was asked for.
 *
 * Those rules bound the result from above — it can never be *harder* than requested — but not from
 * below, since a board may simply not need the hardest technique its ceiling permits. So the carved
 * board is graded and thrown away when it misses, and another attempt is made. That is what
 * [maxAttempts] pays for; in practice a level is hit within a handful of attempts.
 */
class SudokuGenerator(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {

    /**
     * A puzzle at exactly [difficulty].
     *
     * [random] seeds the run. The seed drawn from it is recorded on the puzzle, so a board can be
     * reproduced later from [generateFromSeed] alone.
     */
    fun generate(
        difficulty: Difficulty,
        gameType: GameType = GameType.SOLO,
        random: Random = Random.Default,
    ): Puzzle = generateFromSeed(difficulty, random.nextLong(), gameType)

    /** Deterministic: the same [seed] and [difficulty] always yield the same board. */
    fun generateFromSeed(
        difficulty: Difficulty,
        seed: Long,
        gameType: GameType = GameType.SOLO,
    ): Puzzle {
        val random = Random(seed)

        repeat(maxAttempts) {
            val solution = SudokuSolver.solve(SudokuBoard.EMPTY, random)
                ?: return@repeat
            val givens = carveGivens(solution, difficulty, random)
            if (givens != null) {
                return Puzzle(
                    id = PuzzleId.of(GENERATOR_VERSION, givens, solution),
                    generatorVersion = GENERATOR_VERSION,
                    difficulty = difficulty,
                    gameType = gameType,
                    givens = givens,
                    solution = solution,
                    seed = seed,
                )
            }
        }

        throw IllegalStateException(
            "No $difficulty puzzle found in $maxAttempts attempts from seed $seed",
        )
    }

    /**
     * Removes clues from [solution] for as long as the acceptance rules hold, then returns the board
     * — or `null` when it did not grade at [difficulty] and should be thrown away.
     */
    private fun carveGivens(
        solution: SudokuBoard,
        difficulty: Difficulty,
        random: Random,
    ): SudokuBoard? {
        val allowed = difficulty.allowedTechniques
        var board = solution
        var givens = Sudoku.CELL_COUNT

        for (cell in (0 until Sudoku.CELL_COUNT).shuffled(random)) {
            // The clue floor is what keeps EASY generously clued; every other level floors at the
            // 17 givens below which no board can be unique anyway, so it never binds in practice.
            if (givens <= difficulty.minimumGivens) break
            if (board.isEmpty(cell)) continue

            val candidate = board.withValue(cell, Sudoku.EMPTY)
            if (!SudokuSolver.hasUniqueSolution(candidate)) continue
            if (!LogicalSolver.solve(candidate, allowed).solved) continue

            board = candidate
            givens--
        }

        // Digging under the level's ceiling guarantees the board is no *harder* than asked for. It
        // may still come out easier — a board can simply happen not to need the hardest technique
        // the ceiling permits — so the finished board is graded and rejected when it misses.
        return board.takeIf { DifficultyEvaluator.evaluate(it)?.difficulty == difficulty }
    }

    companion object {
        /**
         * Bumped whenever generation or grading changes in a way that makes older boards' recorded
         * difficulty no longer comparable. It is part of every [Puzzle.id].
         */
        const val GENERATOR_VERSION = 1

        private const val DEFAULT_MAX_ATTEMPTS = 200
    }
}
