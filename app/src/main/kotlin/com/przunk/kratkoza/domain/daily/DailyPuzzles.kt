package com.przunk.kratkoza.domain.daily

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.model.Puzzle
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate

/**
 * The three boards of a given day (project.md section 26).
 *
 * **No server is involved, and none is needed.** The generator is deterministic, so a seed derived
 * from the date produces the same board on every device that asks for it — which is exactly what
 * section 26 requires ("every player gets the same three boards that day"), reached offline.
 *
 * ## The constraint this buys, and it is a real one
 *
 * A board is a function of its seed *and of the generator that reads it*. Change how
 * [SudokuGenerator] carves clues and every past Daily changes with it, retroactively. Two devices on
 * different app versions would then disagree about what "yesterday's hard puzzle" was.
 *
 * Hence [RULESET_VERSION]: it stands for the whole recipe — the seed derivation here *and* the
 * generator's behaviour. Touching either means bumping it and accepting that boards from before the
 * bump belong to the old ruleset. Once rankings exist (stage two), that is the difference between
 * comparable scores and nonsense, so it is worth guarding hard.
 *
 * Generated boards are stored locally the first time they are produced, so a device that has already
 * met today's puzzle keeps it even if the app updates underneath.
 */
object DailyPuzzles {

    /**
     * The recipe version. Bump whenever the seed derivation *or* the generator's output for a given
     * seed changes; past Dailies then belong to the previous version.
     */
    /**
     * Two since 2026-08-29, when Daily's top level moved from Master to Expert.
     *
     * The version stands for the whole recipe, and the set of levels is part of it: rows written
     * under version one describe a day whose hardest board was a Master, which this code would no
     * longer produce. They are ignored rather than reused, so a day keeps the identity it was
     * generated with.
     */
    const val RULESET_VERSION = 2

    /** Easiest, middle, hardest — the three levels agreed for a Daily set. */
    val DIFFICULTIES: List<Difficulty> = listOf(Difficulty.EASY, Difficulty.HARD, Difficulty.EXPERT)

    /**
     * The seed for one board.
     *
     * A SHA-256 of a fixed string rather than [String.hashCode], because the value has to be
     * identical on every device and every version of the platform, and only a specified digest
     * guarantees that.
     */
    fun seedFor(date: LocalDate, difficulty: Difficulty): Long {
        val material = "kratkoza-daily|$RULESET_VERSION|$date|${difficulty.name}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long
    }

    /** The board itself. Costs a generator run, so callers cache it rather than asking twice. */
    fun generate(
        generator: SudokuGenerator,
        date: LocalDate,
        difficulty: Difficulty,
    ): Puzzle = generator.generateFromSeed(
        difficulty = difficulty,
        seed = seedFor(date, difficulty),
        gameType = GameType.DAILY,
    )
}
