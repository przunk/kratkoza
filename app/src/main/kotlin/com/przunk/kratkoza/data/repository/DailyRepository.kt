package com.przunk.kratkoza.data.repository

import com.przunk.kratkoza.data.local.DailyPuzzleDao
import com.przunk.kratkoza.data.local.DailyPuzzleEntity
import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.PuzzleDao
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.domain.daily.DailyEntry
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.daily.DailyStatus
import com.przunk.kratkoza.domain.daily.DailyTotals
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything about the day's three boards (project.md sections 26 to 29).
 *
 * Split out of [GameRepository], which had grown to hold the game lifecycle, the Daily calendar,
 * the statistics and board sharing at once — and had become the file two people had to negotiate
 * over before either could start. Opening and loading a game are still its job; this class asks it
 * for those and owns only what is particular to a Daily.
 */
class DailyRepository(
    private val games: GameRepository,
    private val puzzleDao: PuzzleDao,
    private val dailyPuzzleDao: DailyPuzzleDao,
    private val savedGameDao: SavedGameDao,
    private val historyDao: GameHistoryDao,
    private val generator: SudokuGenerator,
    private val computationDispatcher: CoroutineDispatcher,
) {

    /**
     * Makes checking and claiming a Daily board one process-local operation.
     *
     * [com.przunk.kratkoza.di.AppContainer] owns one of these over one local, single-process
     * database, so a mutex closes every entry point without a schema migration. If the app ever
     * gains another process or another instance of this class, the invariant must move into Room
     * instead — a second mutex would protect nothing while looking like it did.
     */
    private val dailyStartMutex = Mutex()

    /**
     * The three boards of [date], with what the player has done about each (project.md section 26).
     *
     * Generates and stores them the first time the day is asked for, then reads the same rows back
     * for good. Deriving the boards afresh each time would give the same answer today and a
     * different one after any change to the generator — storing them means the day a player met
     * keeps being that day.
     */
    suspend fun dailySet(date: LocalDate): List<DailyEntry> {
        val stored = ensureDailyPuzzles(date)
        val attempts = historyDao.attemptsOn(stored.map { it.puzzleId })

        return stored.map { row ->
            val difficulty = Difficulty.fromStorageName(row.difficulty)
                ?: error("Unreadable difficulty stored for the daily of $date")
            val finished = attempts.firstOrNull { it.puzzleId == row.puzzleId && it.completed }
            val save = savedGameDao.byId(row.puzzleId)

            DailyEntry(
                date = date,
                difficulty = difficulty,
                puzzleId = row.puzzleId,
                status = when {
                    finished != null -> DailyStatus.COMPLETED
                    save != null -> DailyStatus.IN_PROGRESS
                    else -> DailyStatus.NOT_STARTED
                },
                elapsedMillis = finished?.elapsedMillis ?: save?.elapsedMillis,
                mistakeCount = finished?.mistakeCount ?: save?.mistakeCount,
                score = finished?.score,
                rankedEligible = eligibilityOf(
                    attempts = attempts.filter { it.puzzleId == row.puzzleId },
                    save = save,
                ),
            )
        }
    }

    /**
     * Opens one of the day's boards, or `null` when that board is already done.
     *
     * A Daily is solved once. Letting a finished board be played again would let anybody grind the
     * same puzzle until the time looked good, which empties the ranking of meaning — and there is
     * no half-measure available, because the result is already recorded.
     *
     * A board abandoned part-way *can* be picked up again, but not as a ranked attempt: project.md
     * section 28 counts an interrupted game as having lost its place, and deleting the save is as
     * thorough an interruption as there is. Anything after the first attempt is practice.
     */
    suspend fun startDaily(date: LocalDate, difficulty: Difficulty): SavedGame? {
        val row = ensureDailyPuzzles(date).firstOrNull { it.difficulty == difficulty.name }
            ?: error("No daily board for $date at $difficulty")

        return dailyStartMutex.withLock {
            val attempts = historyDao.attemptsOn(listOf(row.puzzleId))
            if (attempts.any { it.completed }) return@withLock null

            games.loadGame(row.puzzleId)?.let { return@withLock it }

            val puzzle = puzzleDao.byId(row.puzzleId)?.toPuzzle()
                ?: error("The daily board for $date at $difficulty is missing from storage")

            games.openGame(puzzle, rankedEligible = eligibilityOf(attempts, save = null))
        }
    }

    /**
     * Whether a Daily board still counts for a ranking.
     *
     * One function because there are two callers and they disagreed: the card said an abandoned
     * board was still eligible — no save, nothing completed, so it looked untouched — while
     * starting it correctly refused to rank a second attempt. A rule written twice is a rule with
     * two answers, and here the wrong one was the one the player could see.
     *
     * The order matters. A finished game reports what it was when it ended; a game in progress
     * reports what it is now; and a board with attempts behind it but nothing on the go has already
     * been interrupted, whatever the absence of a save might suggest.
     */
    private fun eligibilityOf(attempts: List<GameHistoryEntity>, save: SavedGameEntity?): Boolean {
        attempts.firstOrNull { it.completed }?.let { return it.rankedEligible }
        save?.let { return it.rankedEligible }
        return attempts.isEmpty()
    }

    /**
     * The day's boards, generating them once if this device has not met them before.
     *
     * Rows written under an older ruleset are ignored rather than reused: they describe boards this
     * code would no longer produce, and a day whose identity quietly changed is worse than a day
     * generated afresh.
     */
    private suspend fun ensureDailyPuzzles(date: LocalDate): List<DailyPuzzleEntity> {
        val stored = dailyPuzzleDao.forDate(date.toString(), DailyPuzzles.RULESET_VERSION)
        if (stored.size == DailyPuzzles.DIFFICULTIES.size) return stored

        val puzzles = withContext(computationDispatcher) {
            DailyPuzzles.DIFFICULTIES.map { DailyPuzzles.generate(generator, date, it) }
        }
        puzzles.forEach { puzzleDao.insert(it.toEntity()) }
        dailyPuzzleDao.insert(
            puzzles.map {
                DailyPuzzleEntity(
                    date = date.toString(),
                    difficulty = it.difficulty.name,
                    puzzleId = it.id,
                    rulesetVersion = DailyPuzzles.RULESET_VERSION,
                )
            },
        )
        return dailyPuzzleDao.forDate(date.toString(), DailyPuzzles.RULESET_VERSION)
    }


    /**
     * The totals to send to the public boards after finishing one Daily.
     *
     * The day is taken from the board itself rather than from the clock: a game begun before the
     * changeover and finished after it still belongs to the day it was set for, and reading "today"
     * off the clock would file it under the wrong one.
     *
     * Null when the board is not a Daily at all.
     */
    suspend fun dailyTotals(puzzleId: String): DailyTotals? {
        val date = dailyPuzzleDao.dateOf(puzzleId, DailyPuzzles.RULESET_VERSION) ?: return null
        val boards = dailyPuzzleDao.forDate(date, DailyPuzzles.RULESET_VERSION).map { it.puzzleId }

        return DailyTotals(
            today = historyDao.dailyScoreSum(boards) ?: 0L,
            allTime = historyDao.allTimeDailyScoreSum() ?: 0L,
        )
    }
}
