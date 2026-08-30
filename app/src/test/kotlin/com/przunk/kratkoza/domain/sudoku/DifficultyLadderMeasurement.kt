package com.przunk.kratkoza.domain.sudoku

import com.przunk.kratkoza.domain.model.Difficulty
import org.junit.Ignore
import org.junit.Test

/**
 * Measures what the levels actually demand, rather than what they are allowed to.
 *
 * A level's ceiling is the hardest technique its boards *may* need; it says nothing about how many
 * of them need it. project.md section 39 leaves the grading open and the ladder is the app's working
 * answer, so it is worth re-measuring whenever the generator changes — the first such measurement
 * found that most boards were solvable by singles alone, which had left one level unreachable.
 *
 * Not an assertion. It prints, and a person reads it.
 */
class DifficultyLadderMeasurement {

    @Ignore("A measurement, not a check. Run it by name; the results live in OPEN_QUESTIONS.md.")
    @Test
    fun `measure what each level demands`() {
        val generator = SudokuGenerator()
        val report = StringBuilder()

        for (difficulty in Difficulty.entries) {
            val hardest = mutableMapOf<SolvingTechnique, Int>()
            val steps = mutableListOf<Int>()
            val givens = mutableListOf<Int>()

            repeat(SAMPLE) { index ->
                val puzzle = generator.generateFromSeed(difficulty, seed = index.toLong())
                val rating = DifficultyEvaluator.evaluate(puzzle.givens) ?: return@repeat
                val result = LogicalSolver.solve(puzzle.givens)

                hardest.merge(rating.hardestTechnique, 1, Int::plus)
                steps += result.techniqueUsage.values.sum()
                givens += puzzle.givens.filledCount
            }

            val ranks = hardest.entries.flatMap { (technique, count) -> List(count) { technique.rank } }
            report.appendLine(
                "$difficulty ceiling=${difficulty.hardestRequiredTechnique.rank} " +
                    "n=${ranks.size} meanRank=${"%.2f".format(ranks.average())} " +
                    "maxRank=${ranks.maxOrNull()} " +
                    "meanSteps=${"%.1f".format(steps.average())} " +
                    "meanGivens=${"%.1f".format(givens.average())}",
            )
            for ((technique, count) in hardest.entries.sortedBy { it.key.rank }) {
                report.appendLine("    ${technique.name} rank=${technique.rank} boards=$count")
            }
        }
        java.io.File("build/ladder.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }

    private companion object {
        const val SAMPLE = 100
    }
}
