package com.przunk.kratkoza.data

import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.GameEngine
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.game.NO_HISTORY_ROW
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.daily.DailyStatus
import com.przunk.kratkoza.domain.progress.PlayerProgress
import com.przunk.kratkoza.domain.share.BoardCode
import com.przunk.kratkoza.domain.share.BoardImportResult
import com.przunk.kratkoza.domain.progress.Progress
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuFixtures
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRepositoryTest {

    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private var now = 1_000L
    private val DAY: java.time.LocalDate = java.time.LocalDate.parse("2026-08-26")

    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = UnconfinedTestDispatcher(),
        clock = { now },
    )

    @Test
    fun `starting a game stores the board, opens a history row and writes a save`() = runTest {
        val game = repository.startNewGame(Difficulty.NORMAL)

        assertEquals(Difficulty.NORMAL, game.state.puzzle.difficulty)
        assertEquals(GameType.SOLO, game.state.puzzle.gameType)
        assertNotNull(puzzleDao.puzzles[game.state.puzzle.id])

        // project.md section 33 counts started games, so the row exists from the start and is open.
        assertEquals(1, historyDao.all.size)
        assertEquals(false, historyDao.all.single().completed)
        assertNull(historyDao.all.single().finishedAt)

        assertNotNull(repository.loadGame(game.state.puzzle.id))
    }

    @Test
    fun `a solo game is never eligible for a public ranking`() = runTest {
        // project.md section 24.
        assertEquals(false, repository.startNewGame(Difficulty.EASY).rankedEligible)
    }

    @Test
    fun `a game survives a full round trip through storage`() = runTest {
        // project.md sections 21 and 22: the board, the pencil marks, the mistake count and the
        // undo history all have to come back.
        val game = repository.startNewGame(Difficulty.NORMAL)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val wrong = (1..9).first { it != game.state.puzzle.solution[cell] }
        val other = (0 until Sudoku.CELL_COUNT).last { game.state.board.isEmpty(it) }

        var state = GameEngine.enterDigit(game.state, cell, wrong, autoRemoveCandidates = true)
        state = GameEngine.toggleCandidate(state, other, 5)
        state = GameEngine.toggleCandidate(state, other, 9)

        repository.save(game.copy(state = state, elapsedMillis = 61_000))

        val restored = requireNotNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(state.board, restored.state.board)
        assertEquals(state.candidates, restored.state.candidates)
        assertEquals(state.undoStack, restored.state.undoStack)
        assertEquals(1, restored.state.mistakeCount)
        assertEquals(61_000, restored.elapsedMillis)
        assertEquals(game.historyId, restored.historyId)
    }

    @Test
    fun `undo still works on a game that came back from storage`() = runTest {
        val game = repository.startNewGame(Difficulty.NORMAL)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val state = GameEngine.enterDigit(
            game.state,
            cell,
            game.state.puzzle.solution[cell],
            autoRemoveCandidates = true,
        )
        repository.save(game.copy(state = state))

        val restored = requireNotNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(GameEngine.undo(state), GameEngine.undo(restored.state))
    }

    @Test
    fun `finishing a game closes its history row and drops the save`() = runTest {
        val game = repository.startNewGame(Difficulty.HARD)
        now = 95_000
        repository.finishGame(game.copy(elapsedMillis = 90_000), completed = true)

        val entry = historyDao.all.single()
        assertTrue(entry.completed)
        assertEquals(95_000L, entry.finishedAt)
        assertEquals(90_000L, entry.elapsedMillis)
        assertNull(repository.loadGame(game.state.puzzle.id))
    }

    @Test
    fun `abandoning a game leaves it started but not completed`() = runTest {
        val game = repository.startNewGame(Difficulty.EASY)
        repository.deleteSave(game.state.puzzle.id)

        assertNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(1, historyDao.all.size)
        assertEquals(false, historyDao.all.single().completed)

        val statistics = repository.statistics.first()
        assertEquals(1, statistics.startedCount)
        assertEquals(0, statistics.completedCount)
    }

    @Test
    fun `saved games come back newest first`() = runTest {
        val first = repository.startNewGame(Difficulty.EASY)
        now = 2_000
        val second = repository.startNewGame(Difficulty.HARD)

        val saves = repository.savedGames.first()
        assertEquals(listOf(second.state.puzzle.id, first.state.puzzle.id), saves.map { it.puzzleId })
        assertEquals(Difficulty.HARD, saves.first().difficulty)
        assertEquals(second.state.puzzle.givens, saves.first().givens)
    }

    @Test
    fun `statistics are computed from history`() = runTest {
        val easy = repository.startNewGame(Difficulty.EASY)
        now = 2_000
        val hard = repository.startNewGame(Difficulty.HARD)
        now = 3_000
        val secondEasy = repository.startNewGame(Difficulty.EASY)

        repository.finishGame(easy.copy(elapsedMillis = 120_000), completed = true)
        repository.finishGame(secondEasy.copy(elapsedMillis = 60_000), completed = true)
        repository.finishGame(hard.copy(elapsedMillis = 300_000), completed = false)

        val statistics = repository.statistics.first()
        assertEquals(3, statistics.startedCount)
        assertEquals(2, statistics.completedCount)
        assertEquals(2f / 3f, statistics.completionRate, 0.0001f)

        val easyStats = statistics.perDifficulty.single { it.difficulty == Difficulty.EASY }
        assertEquals(2, easyStats.startedCount)
        assertEquals(2, easyStats.completedCount)
        assertEquals(60_000L, easyStats.bestTimeMillis)
        assertEquals(90_000L, easyStats.averageTimeMillis)

        // An abandoned game contributes no time, so a level with nothing completed reports nothing
        // rather than zero.
        val hardStats = statistics.perDifficulty.single { it.difficulty == Difficulty.HARD }
        assertEquals(1, hardStats.startedCount)
        assertEquals(0, hardStats.completedCount)
        assertNull(hardStats.bestTimeMillis)
        assertNull(hardStats.averageTimeMillis)
    }

    @Test
    fun `every level is listed in the statistics even before it is played`() = runTest {
        val statistics = repository.statistics.first()
        assertEquals(Difficulty.entries.toList(), statistics.perDifficulty.map { it.difficulty })
        assertEquals(0f, statistics.completionRate, 0f)
        assertNull(statistics.averageMistakes)
    }

    @Test
    fun `a save whose board cannot be read is reported as missing`() = runTest {
        // A half-restored game is worse than a missing one: the player would be handed a board whose
        // undo silently does nothing.
        val game = repository.startNewGame(Difficulty.EASY)
        val stored = requireNotNull(savedGameDao.byId(game.state.puzzle.id))
        savedGameDao.upsert(stored.copy(undoHistory = "not a move"))

        assertNull(repository.loadGame(game.state.puzzle.id))
    }

    @Test
    fun `resetting statistics takes every counter to zero`() = runTest {
        val first = repository.startNewGame(Difficulty.EASY)
        now = 2_000
        val second = repository.startNewGame(Difficulty.HARD)
        repository.finishGame(first.copy(elapsedMillis = 60_000), completed = true)

        // `second` is still on the board — the case that used to leave the counters standing.
        repository.resetStatistics()

        val statistics = repository.statistics.first()
        assertEquals(0, statistics.startedCount)
        assertEquals(0, statistics.completedCount)
        assertEquals(0, statistics.totalMistakes)
        assertTrue(statistics.perDifficulty.all { it.startedCount == 0 })
        assertNotNull("the game in progress must survive", repository.loadGame(second.state.puzzle.id))
    }

    @Test
    fun `a game finished after a reset is recorded against the fresh record`() = runTest {
        val game = repository.startNewGame(Difficulty.MASTER)
        repository.resetStatistics()

        val reloaded = requireNotNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(NO_HISTORY_ROW, reloaded.historyId)

        now = 9_000
        repository.finishGame(reloaded.copy(elapsedMillis = 45_000), completed = true)

        val statistics = repository.statistics.first()
        assertEquals("started and finished at once, as far as the record is concerned", 1, statistics.startedCount)
        assertEquals(1, statistics.completedCount)
        assertEquals(
            45_000L,
            statistics.perDifficulty.single { it.difficulty == Difficulty.MASTER }.bestTimeMillis,
        )
    }

    @Test
    fun `resetting an empty record is harmless`() = runTest {
        repository.resetStatistics()
        assertEquals(0, repository.statistics.first().startedCount)
    }

    @Test
    fun `finishing a game earns the XP its level is worth`() = runTest {
        // project.md section 32.
        val game = repository.startNewGame(Difficulty.HARD)
        repository.finishGame(game.copy(elapsedMillis = 60_000), completed = true)

        val progress = repository.statistics.first().progress
        assertEquals(PlayerProgress.xpForCompleting(Difficulty.HARD).toLong(), progress.totalXp)
    }

    @Test
    fun `an unfinished game earns nothing`() = runTest {
        val game = repository.startNewGame(Difficulty.MASTER)
        repository.finishGame(game, completed = false)

        assertEquals(0L, repository.statistics.first().progress.totalXp)
    }

    @Test
    fun `mistakes do not reduce XP`() = runTest {
        // The agreed split: XP follows playing, and the penalty for guessing belongs to the ranking
        // (project.md section 29), not here.
        val clean = repository.startNewGame(Difficulty.NORMAL)
        repository.finishGame(clean, completed = true)
        val afterClean = repository.statistics.first().progress.totalXp

        now = 2_000
        val messy = repository.startNewGame(Difficulty.NORMAL)
        var state = messy.state
        val cells = (0 until Sudoku.CELL_COUNT).filter { state.board.isEmpty(it) }.take(4)
        for (cell in cells) {
            state = GameEngine.enterDigit(
                state,
                cell,
                (1..9).first { it != state.puzzle.solution[cell] },
                autoRemoveCandidates = false,
            )
        }
        repository.finishGame(messy.copy(state = state), completed = true)

        assertEquals(4, state.mistakeCount)
        assertEquals(afterClean * 2, repository.statistics.first().progress.totalXp)
    }

    @Test
    fun `XP accumulates into a level`() = runTest {
        // Level 2 costs 100 XP; a MASTER board is worth 120.
        val game = repository.startNewGame(Difficulty.MASTER)
        repository.finishGame(game, completed = true)

        val progress = repository.statistics.first().progress
        assertEquals(120L, progress.totalXp)
        assertEquals(2, progress.level)
        assertEquals(20L, progress.xpIntoLevel)
    }

    @Test
    fun `resetting statistics takes XP with it`() = runTest {
        // XP is derived from history, so wiping the record wipes the level. Honest, and the reset
        // dialog says so.
        val game = repository.startNewGame(Difficulty.EXPERT)
        repository.finishGame(game, completed = true)
        assertTrue(repository.statistics.first().progress.totalXp > 0)

        repository.resetStatistics()
        assertEquals(Progress.NONE, repository.statistics.first().progress)
    }

    @Test
    fun `a shared board can be opened and played`() = runTest {
        val original = repository.startNewGame(Difficulty.HARD)
        val code = BoardCode.encode(original.state.puzzle.givens, original.state.puzzle.generatorVersion)

        // A second device, with nothing in its database.
        val receiver = freshRepository()
        val result = receiver.importSharedBoard(code)

        assertTrue("import said: $result", result is BoardImportResult.Ready)
        val ready = result as BoardImportResult.Ready
        assertFalse(ready.alreadyStarted)

        val imported = requireNotNull(receiver.loadGame(ready.puzzleId))
        assertEquals(original.state.puzzle.givens, imported.state.puzzle.givens)
        assertEquals(original.state.puzzle.solution, imported.state.puzzle.solution)
        assertEquals(original.state.puzzle.id, imported.state.puzzle.id)
    }

    @Test
    fun `the difficulty is graded from the board rather than taken on trust`() = runTest {
        val generator = SudokuGenerator()
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.EXPERT, Difficulty.MASTER)) {
            val puzzle = generator.generate(difficulty, random = Random(difficulty.ordinal + 20L))
            val receiver = freshRepository()

            val result = receiver.importSharedBoard(
                BoardCode.encode(puzzle.givens, puzzle.generatorVersion),
            )

            assertEquals(difficulty, (result as BoardImportResult.Ready).difficulty)
        }
    }

    @Test
    fun `importing a board already in progress resumes it instead of wiping it`() = runTest {
        val game = repository.startNewGame(Difficulty.NORMAL)
        val cell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val played = GameEngine.enterDigit(
            game.state,
            cell,
            game.state.puzzle.solution[cell],
            autoRemoveCandidates = true,
        )
        repository.save(game.copy(state = played, elapsedMillis = 90_000))

        val result = repository.importSharedBoard(
            BoardCode.encode(game.state.puzzle.givens, game.state.puzzle.generatorVersion),
        )

        assertTrue((result as BoardImportResult.Ready).alreadyStarted)
        val resumed = requireNotNull(repository.loadGame(result.puzzleId))
        assertEquals("progress must survive the paste", played.board, resumed.state.board)
        assertEquals(90_000L, resumed.elapsedMillis)
        assertEquals("and no second game may be opened", 1, historyDao.all.size)
    }

    @Test
    fun `a board with more than one solution is refused`() = runTest {
        // project.md section 5. A board from outside has not been through the generator that
        // guarantees a single solution.
        val ambiguous = SudokuBoard.parse(SudokuFixtures.EASY_WITH_TWO_SOLUTIONS)

        assertEquals(
            BoardImportResult.NotAValidPuzzle,
            repository.importSharedBoard(BoardCode.encode(ambiguous, 1)),
        )
    }

    @Test
    fun `an empty board is refused rather than opened as a blank game`() = runTest {
        assertEquals(
            BoardImportResult.NotAValidPuzzle,
            repository.importSharedBoard(BoardCode.encode(SudokuBoard.EMPTY, 1)),
        )
    }

    @Test
    fun `a contradictory board is refused`() = runTest {
        val broken = SudokuBoard.parse(SudokuFixtures.CONTRADICTORY)

        assertEquals(
            BoardImportResult.NotAValidPuzzle,
            repository.importSharedBoard(BoardCode.encode(broken, 1)),
        )
    }

    @Test
    fun `rubbish text and damaged codes are told apart`() = runTest {
        assertEquals(BoardImportResult.NotABoardCode, repository.importSharedBoard("hello"))
        assertEquals(BoardImportResult.NotABoardCode, repository.importSharedBoard(""))

        val code = BoardCode.encode(SudokuBoard.parse(SudokuFixtures.EASY_GIVENS), 1)
        assertEquals(BoardImportResult.Damaged, repository.importSharedBoard(code.dropLast(4)))
    }

    @Test
    fun `a refused board leaves nothing behind`() = runTest {
        repository.importSharedBoard(BoardCode.encode(SudokuBoard.EMPTY, 1))

        assertTrue(repository.savedGames.first().isEmpty())
        assertEquals(0, repository.statistics.first().startedCount)
    }

    /** A second repository over its own empty tables, standing in for another device. */
    private fun freshRepository(): GameRepository {
        val puzzles = FakePuzzleDao()
        val saves = FakeSavedGameDao(puzzles)
        return GameRepository(
            puzzleDao = puzzles,
            dailyPuzzleDao = FakeDailyPuzzleDao(),
            savedGameDao = saves,
            historyDao = FakeGameHistoryDao(saves),
            generator = SudokuGenerator(),
            computationDispatcher = UnconfinedTestDispatcher(),
            clock = { now },
        )
    }

    @Test
    fun `a day offers three untouched boards the first time it is asked for`() = runTest {
        val entries = repository.dailySet(DAY)

        assertEquals(DailyPuzzles.DIFFICULTIES, entries.map { it.difficulty })
        assertTrue(entries.all { it.status == DailyStatus.NOT_STARTED })
        assertTrue("an untouched daily still counts", entries.all { it.rankedEligible })
    }

    @Test
    fun `asking twice returns the same boards, not new ones`() = runTest {
        // The whole point of storing them: a board the player has met must not be swapped out.
        val first = repository.dailySet(DAY).map { it.puzzleId }
        val second = repository.dailySet(DAY).map { it.puzzleId }

        assertEquals(first, second)
    }

    @Test
    fun `two devices see the same boards on the same day`() = runTest {
        // project.md section 26, reached without a server.
        val here = repository.dailySet(DAY).map { it.puzzleId }
        val there = freshRepository().dailySet(DAY).map { it.puzzleId }

        assertEquals(here, there)
    }

    @Test
    fun `starting a daily marks it in progress and eligible for the ranking`() = runTest {
        repository.dailySet(DAY)
        val game = requireNotNull(repository.startDaily(DAY, Difficulty.HARD))

        assertTrue("project.md section 28: a Daily starts eligible", game.rankedEligible)
        assertEquals(GameType.DAILY, game.state.puzzle.gameType)

        val entry = repository.dailySet(DAY).single { it.difficulty == Difficulty.HARD }
        assertEquals(DailyStatus.IN_PROGRESS, entry.status)
    }

    @Test
    fun `opening a daily already under way returns that game rather than restarting it`() = runTest {
        repository.dailySet(DAY)
        val started = requireNotNull(repository.startDaily(DAY, Difficulty.EASY))
        val cell = (0 until Sudoku.CELL_COUNT).first { started.state.board.isEmpty(it) }
        val played = GameEngine.enterDigit(
            started.state,
            cell,
            started.state.puzzle.solution[cell],
            autoRemoveCandidates = true,
        )
        repository.save(started.copy(state = played, elapsedMillis = 30_000))

        val again = requireNotNull(repository.startDaily(DAY, Difficulty.EASY))

        assertEquals(played.board, again.state.board)
        assertEquals(30_000L, again.elapsedMillis)
        assertEquals("no second game may be opened", 1, historyDao.all.size)
    }

    @Test
    fun `a finished daily is reported as completed with its time and mistakes`() = runTest {
        repository.dailySet(DAY)
        val game = requireNotNull(repository.startDaily(DAY, Difficulty.MASTER))
        repository.finishGame(game.copy(elapsedMillis = 240_000), completed = true)

        val entry = repository.dailySet(DAY).single { it.difficulty == Difficulty.MASTER }
        assertEquals(DailyStatus.COMPLETED, entry.status)
        assertEquals(240_000L, entry.elapsedMillis)
    }

    @Test
    fun `each day is a different set of boards`() = runTest {
        val today = repository.dailySet(DAY).map { it.puzzleId }.toSet()
        val tomorrow = repository.dailySet(DAY.plusDays(1)).map { it.puzzleId }.toSet()

        assertTrue("no board may repeat the next day", (today intersect tomorrow).isEmpty())
    }

    @Test
    fun `a daily counts towards statistics and XP like any other game`() = runTest {
        // project.md section 28: a Daily that lost its ranking still feeds the personal record.
        repository.dailySet(DAY)
        val game = requireNotNull(repository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(game.copy(elapsedMillis = 60_000), completed = true)

        val statistics = repository.statistics.first()
        assertEquals(1, statistics.completedCount)
        assertTrue(statistics.progress.totalXp > 0)
    }

    @Test
    fun `a solved daily cannot be played again`() = runTest {
        // Otherwise anybody could grind the same board until the time looked good, and the day's
        // result would mean nothing.
        repository.dailySet(DAY)
        val game = requireNotNull(repository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(game.copy(elapsedMillis = 90_000), completed = true)

        assertNull("a finished Daily is finished", repository.startDaily(DAY, Difficulty.EASY))
        val entry = repository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }
        assertEquals(90_000L, entry.elapsedMillis)
    }

    @Test
    fun `deleting the save does not hand back a fresh ranked attempt`() = runTest {
        // The hole this closes: abandon, restart, and the board is new again. project.md section 28
        // counts an interrupted game as having lost its place, and deleting the save is as thorough
        // an interruption as there is.
        repository.dailySet(DAY)
        val first = requireNotNull(repository.startDaily(DAY, Difficulty.HARD))
        assertTrue(first.rankedEligible)

        repository.deleteSave(first.state.puzzle.id)
        val second = requireNotNull(repository.startDaily(DAY, Difficulty.HARD))

        assertFalse("a second attempt is practice, not a ranked run", second.rankedEligible)
        assertEquals("the same board, not a new one", first.state.puzzle.id, second.state.puzzle.id)
    }

    @Test
    fun `a daily finished without interruption is recorded as having counted`() = runTest {
        // The bug this replaces reported every finished Daily as out of the ranking, because it
        // read publiclyRanked — which asks whether a leaderboard saw the result, not whether the
        // game counted.
        repository.dailySet(DAY)
        val game = requireNotNull(repository.startDaily(DAY, Difficulty.MASTER))
        repository.finishGame(game.copy(elapsedMillis = 120_000), completed = true)

        val entry = repository.dailySet(DAY).single { it.difficulty == Difficulty.MASTER }
        assertEquals(DailyStatus.COMPLETED, entry.status)
        assertTrue("it was played straight through, so it counted", entry.rankedEligible)
    }

    @Test
    fun `a daily finished after losing eligibility is recorded as not having counted`() = runTest {
        repository.dailySet(DAY)
        val game = requireNotNull(repository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(game.copy(rankedEligible = false, elapsedMillis = 60_000), completed = true)

        val entry = repository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }
        assertFalse(entry.rankedEligible)
    }
}
