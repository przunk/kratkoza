package com.przunk.kratkoza.data

import com.przunk.kratkoza.data.repository.DailyRepository
import com.przunk.kratkoza.data.repository.StatisticsRepository
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.domain.game.GameEngine
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.game.NO_HISTORY_ROW
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.daily.DailyScore
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    private val dailyPuzzleDao = FakeDailyPuzzleDao()
    private val solvedBoardDao = FakeSolvedBoardDao(puzzleDao, savedGameDao)
    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        solvedBoardDao = solvedBoardDao,
        generator = SudokuGenerator(),
        computationDispatcher = UnconfinedTestDispatcher(),
        clock = { now },
    )
    private val dailyRepository = DailyRepository(
        games = repository,
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
    )
    private val statisticsRepository = StatisticsRepository(historyDao, savedGameDao)

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

        repository.save(game.copy(state = state, elapsedMillis = 61_000, hintCount = 2))

        val restored = requireNotNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(state.board, restored.state.board)
        assertEquals(state.candidates, restored.state.candidates)
        assertEquals(state.undoStack, restored.state.undoStack)
        assertEquals(1, restored.state.mistakeCount)
        assertEquals(2, restored.hintCount)
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
    fun `a successful solo game is archived with its original board and result`() = runTest {
        val game = repository.startNewGame(Difficulty.HARD)
        val wrongCell = (0 until Sudoku.CELL_COUNT).first { game.state.board.isEmpty(it) }
        val wrong = (1..9).first { it != game.state.puzzle.solution[wrongCell] }
        val state = GameEngine.enterDigit(
            game.state,
            wrongCell,
            wrong,
            autoRemoveCandidates = true,
        )
        now = 95_000

        repository.finishGame(
            game.copy(state = state, elapsedMillis = 90_000, hintCount = 3),
            completed = true,
        )

        val archived = repository.solvedBoards.first().single()
        assertEquals(game.state.puzzle.id, archived.puzzleId)
        assertEquals(game.state.puzzle.givens, archived.givens)
        assertEquals(Difficulty.HARD, archived.difficulty)
        assertEquals(95_000L, archived.completedAt)
        assertEquals(90_000L, archived.elapsedMillis)
        assertEquals(1, archived.mistakeCount)
        assertEquals(3, archived.hintCount)
        assertEquals(PlayerProgress.xpForCompleting(Difficulty.HARD), archived.xpEarned)
        assertEquals(1, archived.completionCount)
        assertEquals(3, historyDao.all.single().hintCount)
    }

    @Test
    fun `unfinished and daily games do not enter the solo archive`() = runTest {
        val unfinished = repository.startNewGame(Difficulty.EASY)
        repository.finishGame(unfinished, completed = false)

        dailyRepository.dailySet(DAY)
        val daily = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(daily.copy(elapsedMillis = 60_000), completed = true)

        assertTrue(repository.solvedBoards.first().isEmpty())
    }

    @Test
    fun `replaying a solved board resumes an existing attempt and counts another completion`() = runTest {
        val original = repository.startNewGame(Difficulty.NORMAL)
        repository.finishGame(original.copy(elapsedMillis = 60_000), completed = true)
        val firstArchiveId = repository.solvedBoards.first().single().id

        val replay = requireNotNull(repository.replaySolvedBoard(original.state.puzzle.id))
        val resumed = requireNotNull(repository.replaySolvedBoard(original.state.puzzle.id))
        assertEquals(replay.historyId, resumed.historyId)
        assertEquals(2, historyDao.all.size)
        assertTrue(repository.solvedBoard(firstArchiveId).first()!!.inProgress)

        now = 9_000
        repository.finishGame(replay.copy(elapsedMillis = 45_000), completed = true)

        val archive = repository.solvedBoards.first()
        assertEquals(2, archive.size)
        assertTrue(archive.all { it.completionCount == 2 })
        assertFalse(archive.first { it.id == firstArchiveId }.inProgress)
    }

    @Test
    fun `abandoning a game leaves it started but not completed`() = runTest {
        val game = repository.startNewGame(Difficulty.EASY)
        repository.deleteSave(game.state.puzzle.id)

        assertNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(1, historyDao.all.size)
        assertEquals(false, historyDao.all.single().completed)

        val statistics = statisticsRepository.statistics.first()
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

        val statistics = statisticsRepository.statistics.first()
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
        val statistics = statisticsRepository.statistics.first()
        assertEquals(Difficulty.entries.toList(), statistics.perDifficulty.map { it.difficulty })
        assertEquals(0f, statistics.completionRate, 0f)
        assertNull(statistics.averageMistakes)
        assertNull(statistics.daily)
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
        statisticsRepository.resetStatistics()

        val statistics = statisticsRepository.statistics.first()
        assertEquals(0, statistics.startedCount)
        assertEquals(0, statistics.completedCount)
        assertEquals(0, statistics.totalMistakes)
        assertTrue(statistics.perDifficulty.all { it.startedCount == 0 })
        assertNotNull("the game in progress must survive", repository.loadGame(second.state.puzzle.id))
        assertEquals("the solved-board archive is independent of statistics", 1, repository.solvedBoards.first().size)
    }

    @Test
    fun `a game finished after a reset is recorded against the fresh record`() = runTest {
        val game = repository.startNewGame(Difficulty.MASTER)
        statisticsRepository.resetStatistics()

        val reloaded = requireNotNull(repository.loadGame(game.state.puzzle.id))
        assertEquals(NO_HISTORY_ROW, reloaded.historyId)

        now = 9_000
        repository.finishGame(reloaded.copy(elapsedMillis = 45_000), completed = true)

        val statistics = statisticsRepository.statistics.first()
        assertEquals("started and finished at once, as far as the record is concerned", 1, statistics.startedCount)
        assertEquals(1, statistics.completedCount)
        assertEquals(
            45_000L,
            statistics.perDifficulty.single { it.difficulty == Difficulty.MASTER }.bestTimeMillis,
        )
    }

    @Test
    fun `resetting an empty record is harmless`() = runTest {
        statisticsRepository.resetStatistics()
        assertEquals(0, statisticsRepository.statistics.first().startedCount)
    }

    @Test
    fun `finishing a game earns the XP its level is worth`() = runTest {
        // project.md section 32.
        val game = repository.startNewGame(Difficulty.HARD)
        repository.finishGame(game.copy(elapsedMillis = 60_000), completed = true)

        val progress = statisticsRepository.statistics.first().progress
        assertEquals(PlayerProgress.xpForCompleting(Difficulty.HARD).toLong(), progress.totalXp)
    }

    @Test
    fun `an unfinished game earns nothing`() = runTest {
        val game = repository.startNewGame(Difficulty.MASTER)
        repository.finishGame(game, completed = false)

        assertEquals(0L, statisticsRepository.statistics.first().progress.totalXp)
    }

    @Test
    fun `mistakes do not reduce XP`() = runTest {
        // The agreed split: XP follows playing, and the penalty for guessing belongs to the ranking
        // (project.md section 29), not here.
        val clean = repository.startNewGame(Difficulty.NORMAL)
        repository.finishGame(clean, completed = true)
        val afterClean = statisticsRepository.statistics.first().progress.totalXp

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
        assertEquals(afterClean * 2, statisticsRepository.statistics.first().progress.totalXp)
    }

    @Test
    fun `XP accumulates into a level`() = runTest {
        // Level 2 costs 100 XP; a MASTER board is worth 120.
        val game = repository.startNewGame(Difficulty.MASTER)
        repository.finishGame(game, completed = true)

        val progress = statisticsRepository.statistics.first().progress
        assertEquals(120L, progress.totalXp)
        assertEquals(2, progress.level)
        assertEquals(20L, progress.xpIntoLevel)
    }

    @Test
    fun `resetting statistics takes XP with it`() = runTest {
        // XP is derived from history, so wiping the record wipes the level. Honest, and the reset
        // dialog says so.
        val game = repository.startNewGame(Difficulty.MASTER)
        repository.finishGame(game, completed = true)
        assertTrue(statisticsRepository.statistics.first().progress.totalXp > 0)

        statisticsRepository.resetStatistics()
        assertEquals(Progress.NONE, statisticsRepository.statistics.first().progress)
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
    fun `a successfully completed shared board is included in the archive`() = runTest {
        val original = repository.startNewGame(Difficulty.HARD)
        val code = BoardCode.encode(
            original.state.puzzle.givens,
            original.state.puzzle.generatorVersion,
        )
        val receiverPuzzles = FakePuzzleDao()
        val receiverSaves = FakeSavedGameDao(receiverPuzzles)
        val receiverArchive = FakeSolvedBoardDao(receiverPuzzles, receiverSaves)
        val receiver = GameRepository(
            puzzleDao = receiverPuzzles,
            dailyPuzzleDao = FakeDailyPuzzleDao(),
            savedGameDao = receiverSaves,
            historyDao = FakeGameHistoryDao(receiverSaves),
            solvedBoardDao = receiverArchive,
            generator = SudokuGenerator(),
            computationDispatcher = UnconfinedTestDispatcher(),
            clock = { now },
        )

        val ready = receiver.importSharedBoard(code) as BoardImportResult.Ready
        val imported = requireNotNull(receiver.loadGame(ready.puzzleId))
        receiver.finishGame(imported.copy(elapsedMillis = 80_000), completed = true)

        assertTrue(receiver.solvedBoards.first().single().imported)
    }

    @Test
    fun `the difficulty is graded from the board rather than taken on trust`() = runTest {
        val generator = SudokuGenerator()
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.MASTER, Difficulty.MASTER)) {
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
        assertEquals(0, statisticsRepository.statistics.first().startedCount)
    }

    /** A second repository over its own empty tables, standing in for another device. */
    /** A second device: its own database, its own repositories. */
    private fun freshRepository(): GameRepository {
        val puzzles = FakePuzzleDao()
        val saves = FakeSavedGameDao(puzzles)
        return GameRepository(
            puzzleDao = puzzles,
            dailyPuzzleDao = FakeDailyPuzzleDao(),
            savedGameDao = saves,
            historyDao = FakeGameHistoryDao(saves),
            solvedBoardDao = FakeSolvedBoardDao(puzzles, saves),
            generator = SudokuGenerator(),
            computationDispatcher = UnconfinedTestDispatcher(),
            clock = { now },
        )
    }

    /** The same second device, asked about the Daily calendar rather than about a game. */
    private fun freshDailyRepository(): DailyRepository {
        val puzzles = FakePuzzleDao()
        val saves = FakeSavedGameDao(puzzles)
        val history = FakeGameHistoryDao(saves)
        val dailyPuzzles = FakeDailyPuzzleDao()
        return DailyRepository(
            games = GameRepository(
                puzzleDao = puzzles,
                dailyPuzzleDao = dailyPuzzles,
                savedGameDao = saves,
                historyDao = history,
                solvedBoardDao = FakeSolvedBoardDao(puzzles, saves),
                generator = SudokuGenerator(),
                computationDispatcher = UnconfinedTestDispatcher(),
                clock = { now },
            ),
            puzzleDao = puzzles,
            dailyPuzzleDao = dailyPuzzles,
            savedGameDao = saves,
            historyDao = history,
            generator = SudokuGenerator(),
            computationDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `a day offers three untouched boards the first time it is asked for`() = runTest {
        val entries = dailyRepository.dailySet(DAY)

        assertEquals(DailyPuzzles.DIFFICULTIES, entries.map { it.difficulty })
        assertTrue(entries.all { it.status == DailyStatus.NOT_STARTED })
        assertTrue("an untouched daily still counts", entries.all { it.rankedEligible })
    }

    @Test
    fun `asking twice returns the same boards, not new ones`() = runTest {
        // The whole point of storing them: a board the player has met must not be swapped out.
        val first = dailyRepository.dailySet(DAY).map { it.puzzleId }
        val second = dailyRepository.dailySet(DAY).map { it.puzzleId }

        assertEquals(first, second)
    }

    @Test
    fun `two devices see the same boards on the same day`() = runTest {
        // project.md section 26, reached without a server.
        val here = dailyRepository.dailySet(DAY).map { it.puzzleId }
        val there = freshDailyRepository().dailySet(DAY).map { it.puzzleId }

        assertEquals(here, there)
    }

    @Test
    fun `starting a daily marks it in progress and eligible for the ranking`() = runTest {
        dailyRepository.dailySet(DAY)
        val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.HARD))

        assertTrue("project.md section 28: a Daily starts eligible", game.rankedEligible)
        assertEquals(GameType.DAILY, game.state.puzzle.gameType)

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.HARD }
        assertEquals(DailyStatus.IN_PROGRESS, entry.status)
    }

    @Test
    fun `opening a daily already under way returns that game rather than restarting it`() = runTest {
        dailyRepository.dailySet(DAY)
        val started = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        val cell = (0 until Sudoku.CELL_COUNT).first { started.state.board.isEmpty(it) }
        val played = GameEngine.enterDigit(
            started.state,
            cell,
            started.state.puzzle.solution[cell],
            autoRemoveCandidates = true,
        )
        repository.save(started.copy(state = played, elapsedMillis = 30_000))

        val again = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))

        assertEquals(played.board, again.state.board)
        assertEquals(30_000L, again.elapsedMillis)
        assertEquals("no second game may be opened", 1, historyDao.all.size)
    }

    @Test
    fun `two concurrent Daily starts create one attempt and return that same game`() = runTest {
        val racingPuzzles = RacingPuzzleDao()
        val racingSaves = FakeSavedGameDao(racingPuzzles)
        val racingHistory = FakeGameHistoryDao(racingSaves)
        val racingDailyPuzzles = FakeDailyPuzzleDao()
        val racingRepository = DailyRepository(
            games = GameRepository(
                puzzleDao = racingPuzzles,
                dailyPuzzleDao = racingDailyPuzzles,
                savedGameDao = racingSaves,
                historyDao = racingHistory,
                solvedBoardDao = FakeSolvedBoardDao(racingPuzzles, racingSaves),
                generator = SudokuGenerator(),
                computationDispatcher = Dispatchers.Default,
                clock = { now },
            ),
            puzzleDao = racingPuzzles,
            dailyPuzzleDao = racingDailyPuzzles,
            savedGameDao = racingSaves,
            historyDao = racingHistory,
            generator = SudokuGenerator(),
            computationDispatcher = Dispatchers.Default,
        )
        racingRepository.dailySet(DAY)
        racingPuzzles.armRace()

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val calls = List(2) {
            async(Dispatchers.Default) {
                ready.countDown()
                check(start.await(2, TimeUnit.SECONDS))
                racingRepository.startDaily(DAY, Difficulty.EASY)
            }
        }
        check(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        val games = calls.awaitAll().map(::requireNotNull)

        assertEquals(1, racingHistory.all.count { it.puzzleId == games.first().state.puzzle.id })
        assertEquals(1, racingSaves.writeCount)
        assertEquals(games.first().historyId, games.last().historyId)
    }

    @Test
    fun `a finished daily is reported as completed with its time and mistakes`() = runTest {
        dailyRepository.dailySet(DAY)
        val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EXPERT))
        repository.finishGame(game.copy(elapsedMillis = 240_000), completed = true)

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EXPERT }
        assertEquals(DailyStatus.COMPLETED, entry.status)
        assertEquals(240_000L, entry.elapsedMillis)
    }

    @Test
    fun `each day is a different set of boards`() = runTest {
        val today = dailyRepository.dailySet(DAY).map { it.puzzleId }.toSet()
        val tomorrow = dailyRepository.dailySet(DAY.plusDays(1)).map { it.puzzleId }.toSet()

        assertTrue("no board may repeat the next day", (today intersect tomorrow).isEmpty())
    }

    @Test
    fun `a daily counts towards statistics and XP like any other game`() = runTest {
        // project.md section 28: a Daily that lost its ranking still feeds the personal record.
        dailyRepository.dailySet(DAY)
        val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(game.copy(elapsedMillis = 60_000), completed = true)

        val statistics = statisticsRepository.statistics.first()
        assertEquals(1, statistics.completedCount)
        assertTrue(statistics.progress.totalXp > 0)

        val daily = requireNotNull(statistics.daily)
        val expectedScore = DailyScore.of(Difficulty.EASY, elapsedMillis = 60_000, mistakeCount = 0)
        assertEquals(1, daily.completedCount)
        assertEquals(expectedScore.toLong(), daily.totalScore)
        assertEquals(expectedScore, daily.bestScore)
        assertEquals(expectedScore.toDouble(), daily.averageScore!!, 0.001)
        assertEquals(1, daily.rankedEligibleCount)
        assertEquals(
            listOf(Difficulty.EASY to 1),
            daily.perDifficulty.map { it.difficulty to it.completedCount },
        )
    }

    @Test
    fun `a solved daily cannot be played again`() = runTest {
        // Otherwise anybody could grind the same board until the time looked good, and the day's
        // result would mean nothing.
        dailyRepository.dailySet(DAY)
        val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(game.copy(elapsedMillis = 90_000), completed = true)

        assertNull("a finished Daily is finished", dailyRepository.startDaily(DAY, Difficulty.EASY))
        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }
        assertEquals(90_000L, entry.elapsedMillis)
    }

    @Test
    fun `deleting the save does not hand back a fresh ranked attempt`() = runTest {
        // The hole this closes: abandon, restart, and the board is new again. project.md section 28
        // counts an interrupted game as having lost its place, and deleting the save is as thorough
        // an interruption as there is.
        dailyRepository.dailySet(DAY)
        val first = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.HARD))
        assertTrue(first.rankedEligible)

        repository.deleteSave(first.state.puzzle.id)
        val second = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.HARD))

        assertFalse("a second attempt is practice, not a ranked run", second.rankedEligible)
        assertEquals("the same board, not a new one", first.state.puzzle.id, second.state.puzzle.id)
    }

    @Test
    fun `a daily finished without interruption is recorded as having counted`() = runTest {
        // The bug this replaces reported every finished Daily as out of the ranking, because it
        // read publiclyRanked — which asks whether a leaderboard saw the result, not whether the
        // game counted.
        dailyRepository.dailySet(DAY)
        val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EXPERT))
        repository.finishGame(game.copy(elapsedMillis = 120_000), completed = true)

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EXPERT }
        assertEquals(DailyStatus.COMPLETED, entry.status)
        assertTrue("it was played straight through, so it counted", entry.rankedEligible)
    }

    @Test
    fun `a daily finished after losing eligibility is recorded as not having counted`() = runTest {
        dailyRepository.dailySet(DAY)
        val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(game.copy(rankedEligible = false, elapsedMillis = 60_000), completed = true)

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }
        assertFalse(entry.rankedEligible)
    }

    @Test
    fun `an abandoned daily stops advertising itself as ranked`() = runTest {
        // The card and the start button used to answer this differently: with the save gone and
        // nothing completed, the board looked untouched, so the card promised a ranked run that
        // starting it would not have given.
        dailyRepository.dailySet(DAY)
        val first = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.HARD))
        repository.deleteSave(first.state.puzzle.id)

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.HARD }
        assertEquals(DailyStatus.NOT_STARTED, entry.status)
        assertFalse("the card must not promise what starting would refuse", entry.rankedEligible)

        val second = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.HARD))
        assertEquals("and the two must agree", entry.rankedEligible, second.rankedEligible)
    }

    @Test
    fun `abandoning repeatedly never restores eligibility`() = runTest {
        dailyRepository.dailySet(DAY)
        repeat(3) {
            val game = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
            repository.deleteSave(game.state.puzzle.id)
        }

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }
        assertFalse(entry.rankedEligible)
        assertFalse(requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY)).rankedEligible)
    }

    @Test
    fun `an untouched board still reports itself as ranked`() = runTest {
        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EXPERT }

        assertEquals(DailyStatus.NOT_STARTED, entry.status)
        assertTrue(entry.rankedEligible)
    }

    @Test
    fun `a finished daily is scored and a solo game is not`() = runTest {
        // project.md section 29 scores a Daily against a shared board. A solo game has nothing to be
        // compared against, so it has no score — which is not the same as a score of zero.
        dailyRepository.dailySet(DAY)
        val daily = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.HARD))
        val elapsed = DailyScore.targetMinutesFor(Difficulty.HARD) * 60_000L
        repository.finishGame(daily.copy(elapsedMillis = elapsed), completed = true)

        // Compared against DailyScore rather than a number, and against the whole of it rather than
        // one property of its shape. This test is about the score being computed and stored at all;
        // what the formula says is DailyScoreTest's business, and an earlier version of this line
        // pinned a property that the formula later stopped having.
        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.HARD }
        assertEquals(
            "the stored score must be the one DailyScore gives for this game",
            DailyScore.of(Difficulty.HARD, elapsed, mistakeCount = 0),
            entry.score,
        )

        val solo = repository.startNewGame(Difficulty.HARD)
        repository.finishGame(solo.copy(elapsedMillis = 60_000), completed = true)
        assertTrue(historyDao.all.single { it.puzzleId == solo.state.puzzle.id }.score == null)
    }

    @Test
    fun `an unfinished daily has no score rather than a zero`() = runTest {
        dailyRepository.dailySet(DAY)
        val daily = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        repository.finishGame(daily.copy(elapsedMillis = 60_000), completed = false)

        assertNull(dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }.score)
    }

    @Test
    fun `mistakes on a daily cost score, exactly as section 29 says`() = runTest {
        dailyRepository.dailySet(DAY)
        val daily = requireNotNull(dailyRepository.startDaily(DAY, Difficulty.EASY))
        var state = daily.state
        val cells = (0 until Sudoku.CELL_COUNT).filter { state.board.isEmpty(it) }.take(2)
        for (cell in cells) {
            state = GameEngine.enterDigit(
                state,
                cell,
                (1..9).first { it != state.puzzle.solution[cell] },
                autoRemoveCandidates = false,
            )
        }
        val target = DailyScore.targetMinutesFor(Difficulty.EASY) * 60_000L
        repository.finishGame(
            daily.copy(state = state, elapsedMillis = target),
            completed = true,
        )

        val entry = dailyRepository.dailySet(DAY).single { it.difficulty == Difficulty.EASY }
        assertEquals(2, entry.mistakeCount)
        assertEquals(
            "the base, less section 29's penalty for two mistakes",
            DailyScore.of(Difficulty.EASY, target, mistakeCount = 2),
            entry.score,
        )
        assertTrue("and that has to be a real loss", entry.score!! < DailyScore.baseFor(Difficulty.EASY))
    }
}

/**
 * Holds two callers after both have observed that no save exists yet.
 *
 * The timeout is intentional: once the repository serializes the critical section, the second
 * caller cannot arrive until the first has claimed the board. Without serialization both callers
 * meet here and deterministically continue from the same stale state.
 */
private class RacingPuzzleDao : FakePuzzleDao() {
    private val firstArrived = AtomicBoolean(false)
    private val releaseFirst = CountDownLatch(1)

    @Volatile
    private var raceArmed = false

    fun armRace() {
        raceArmed = true
    }

    override suspend fun byId(puzzleId: String): PuzzleEntity? {
        val puzzle = super.byId(puzzleId)
        if (!raceArmed) return puzzle

        if (firstArrived.compareAndSet(false, true)) {
            releaseFirst.await(500, TimeUnit.MILLISECONDS)
        } else {
            releaseFirst.countDown()
        }
        return puzzle
    }
}
