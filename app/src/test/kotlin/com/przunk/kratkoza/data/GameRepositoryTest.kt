package com.przunk.kratkoza.data

import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.GameEngine
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.game.NO_HISTORY_ROW
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRepositoryTest {

    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private var now = 1_000L

    private val repository = GameRepository(
        puzzleDao = puzzleDao,
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
}
