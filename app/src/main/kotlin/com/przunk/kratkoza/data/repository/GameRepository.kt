package com.przunk.kratkoza.data.repository

import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.MoveHistoryCodec
import com.przunk.kratkoza.data.local.PuzzleDao
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.local.SavedGameSummaryRow
import com.przunk.kratkoza.domain.game.CandidateGrid
import com.przunk.kratkoza.domain.game.GameState
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.game.SavedGameSummary
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.model.Puzzle
import com.przunk.kratkoza.domain.stats.DifficultyStatistics
import com.przunk.kratkoza.domain.stats.Statistics
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Games, saves, history and statistics — everything the app persists about play
 * (technical_requirements.md sections 4 and 6).
 *
 * The domain types never leave this class as database rows: generating, saving and resuming all
 * speak [Puzzle], [GameState] and [SavedGame], which is what keeps the game rules free of Room.
 */
class GameRepository(
    private val puzzleDao: PuzzleDao,
    private val savedGameDao: SavedGameDao,
    private val historyDao: GameHistoryDao,
    private val generator: SudokuGenerator,
    /** Generation is a few tens of milliseconds of pure CPU; it must not run on the main thread. */
    private val computationDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Starts a new game: generates a board, stores it, and opens the history row that project.md
     * section 33 counts as a started game.
     */
    suspend fun startNewGame(difficulty: Difficulty): SavedGame {
        val puzzle = withContext(computationDispatcher) {
            generator.generate(difficulty, GameType.SOLO)
        }
        puzzleDao.insert(puzzle.toEntity())

        val now = clock()
        val historyId = historyDao.insert(
            GameHistoryEntity(
                puzzleId = puzzle.id,
                gameType = puzzle.gameType.name,
                difficulty = puzzle.difficulty.name,
                startedAt = now,
                finishedAt = null,
                elapsedMillis = 0,
                mistakeCount = 0,
                completed = false,
                score = null,
                xpEarned = 0,
                publiclyRanked = false,
                autoRemoveCandidates = true,
                rulesetVersion = SudokuGenerator.GENERATOR_VERSION,
            ),
        )

        val save = SavedGame(
            state = GameState.newGame(puzzle),
            startedAt = now,
            lastPlayedAt = now,
            elapsedMillis = 0,
            historyId = historyId,
            // project.md section 24: solo games have no public leaderboard.
            rankedEligible = false,
        )
        save(save)
        return save
    }

    /**
     * Writes the game out (project.md section 21).
     *
     * Called after every meaningful move, so it has to be cheap: one upsert of one row, no reads.
     */
    suspend fun save(game: SavedGame) {
        savedGameDao.upsert(
            SavedGameEntity(
                puzzleId = game.state.puzzle.id,
                historyId = game.historyId,
                startedAt = game.startedAt,
                lastPlayedAt = clock(),
                elapsedMillis = game.elapsedMillis,
                board = game.state.board.toStorageString(),
                candidates = game.state.candidates.toStorageString(),
                undoHistory = MoveHistoryCodec.encode(game.state.undoStack),
                mistakeCount = game.state.mistakeCount,
                rankedEligible = game.rankedEligible,
            ),
        )
    }

    /**
     * Reloads a saved game, or `null` when there is nothing to reload.
     *
     * Returns `null` too when the save cannot be read back — a board of the wrong length, an undo
     * stack that will not parse. A half-restored game would be worse than a missing one: the player
     * would be handed a board whose undo silently does nothing.
     */
    suspend fun loadGame(puzzleId: String): SavedGame? {
        val save = savedGameDao.byId(puzzleId) ?: return null
        val puzzle = puzzleDao.byId(puzzleId)?.toPuzzle() ?: return null

        return try {
            SavedGame(
                state = GameState(
                    puzzle = puzzle,
                    board = SudokuBoard.parse(save.board),
                    candidates = CandidateGrid.parse(save.candidates),
                    mistakeCount = save.mistakeCount,
                    undoStack = MoveHistoryCodec.decode(save.undoHistory),
                ),
                startedAt = save.startedAt,
                lastPlayedAt = save.lastPlayedAt,
                elapsedMillis = save.elapsedMillis,
                historyId = save.historyId,
                rankedEligible = save.rankedEligible,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Closes a game out: updates its history row and drops the save.
     *
     * [completed] is the caller's answer to "is the board correctly solved" — project.md section 16
     * is explicit that a game only counts as finished when it actually is.
     */
    suspend fun finishGame(game: SavedGame, completed: Boolean) {
        historyDao.markFinished(
            id = game.historyId,
            finishedAt = clock(),
            elapsedMillis = game.elapsedMillis,
            mistakeCount = game.state.mistakeCount,
            completed = completed,
        )
        savedGameDao.delete(game.state.puzzle.id)
    }

    /** Abandons a game. The history row stays open, so it still counts as started but not completed. */
    suspend fun deleteSave(puzzleId: String) = savedGameDao.delete(puzzleId)

    /** Unfinished games, most recently played first (project.md section 22). */
    val savedGames: Flow<List<SavedGameSummary>> =
        savedGameDao.observeAll().map { rows -> rows.mapNotNull(SavedGameSummaryRow::toSummary) }

    /** The game the Home screen offers to continue (project.md section 22). */
    val mostRecentSaveId: Flow<String?> = savedGameDao.observeMostRecentId()

    /** The player's record (project.md section 33), recomputed from history on every change. */
    val statistics: Flow<Statistics> = combine(
        historyDao.observeStartedCount(),
        historyDao.observeCompletedCount(),
        historyDao.observeTotalMistakes(),
        historyDao.observeByDifficulty(),
    ) { started, completed, mistakes, perDifficulty ->
        Statistics(
            startedCount = started,
            completedCount = completed,
            totalMistakes = mistakes,
            perDifficulty = Difficulty.entries.map { difficulty ->
                val row = perDifficulty.firstOrNull { it.difficulty == difficulty.name }
                DifficultyStatistics(
                    difficulty = difficulty,
                    startedCount = row?.startedCount ?: 0,
                    completedCount = row?.completedCount ?: 0,
                    bestTimeMillis = row?.bestTimeMillis,
                    averageTimeMillis = row?.averageTimeMillis?.roundToLong(),
                    averageMistakes = row?.averageMistakes,
                )
            },
        )
    }
}

private fun Puzzle.toEntity() = PuzzleEntity(
    puzzleId = id,
    generatorVersion = generatorVersion,
    difficulty = difficulty.name,
    gameType = gameType.name,
    givens = givens.toStorageString(),
    solution = solution.toStorageString(),
    seed = seed,
)

/** `null` when the stored row cannot be read as a puzzle; the caller treats that as "not there". */
private fun PuzzleEntity.toPuzzle(): Puzzle? = try {
    Puzzle(
        id = puzzleId,
        generatorVersion = generatorVersion,
        difficulty = Difficulty.fromStorageName(difficulty) ?: return null,
        gameType = GameType.fromStorageName(gameType) ?: return null,
        givens = SudokuBoard.parse(givens),
        solution = SudokuBoard.parse(solution),
        seed = seed,
    )
} catch (_: IllegalArgumentException) {
    null
}

private fun SavedGameSummaryRow.toSummary(): SavedGameSummary? = try {
    SavedGameSummary(
        puzzleId = puzzleId,
        difficulty = Difficulty.fromStorageName(difficulty) ?: return null,
        gameType = GameType.fromStorageName(gameType) ?: return null,
        startedAt = startedAt,
        lastPlayedAt = lastPlayedAt,
        elapsedMillis = elapsedMillis,
        mistakeCount = mistakeCount,
        board = SudokuBoard.parse(board),
        givens = SudokuBoard.parse(givens),
    )
} catch (_: IllegalArgumentException) {
    null
}
