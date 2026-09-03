package com.przunk.kratkoza.data.repository

import com.przunk.kratkoza.data.local.GameHistoryDao
import com.przunk.kratkoza.data.local.DifficultyStatisticsRow
import com.przunk.kratkoza.data.local.DailyDifficultyStatisticsRow
import com.przunk.kratkoza.data.local.DailyPuzzleDao
import com.przunk.kratkoza.data.local.DailyPuzzleEntity
import com.przunk.kratkoza.data.local.DailyStatisticsSummaryRow
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.local.InProgressCountRow
import com.przunk.kratkoza.data.local.MoveHistoryCodec
import com.przunk.kratkoza.data.local.PuzzleDao
import com.przunk.kratkoza.data.local.PuzzleEntity
import com.przunk.kratkoza.data.local.SavedGameDao
import com.przunk.kratkoza.data.local.SavedGameEntity
import com.przunk.kratkoza.data.local.SavedGameSummaryRow
import com.przunk.kratkoza.data.local.SolvedBoardDao
import com.przunk.kratkoza.data.local.SolvedBoardEntity
import com.przunk.kratkoza.data.local.SolvedBoardRow
import com.przunk.kratkoza.domain.daily.DailyEntry
import com.przunk.kratkoza.domain.daily.DailyTotals
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.daily.DailySchedule
import com.przunk.kratkoza.domain.daily.DailyScore
import com.przunk.kratkoza.domain.daily.DailyStatus
import com.przunk.kratkoza.domain.game.CandidateGrid
import com.przunk.kratkoza.domain.game.GameState
import com.przunk.kratkoza.domain.game.FinishedGame
import com.przunk.kratkoza.domain.game.NO_HISTORY_ROW
import com.przunk.kratkoza.domain.game.SavedGame
import com.przunk.kratkoza.domain.game.SavedGameSummary
import com.przunk.kratkoza.domain.game.SolvedBoard
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.model.Puzzle
import com.przunk.kratkoza.domain.progress.PlayerProgress
import com.przunk.kratkoza.domain.share.BoardCode
import com.przunk.kratkoza.domain.share.BoardImportResult
import com.przunk.kratkoza.domain.stats.DifficultyStatistics
import com.przunk.kratkoza.domain.stats.DailyDifficultyStatistics
import com.przunk.kratkoza.domain.stats.DailyStatistics
import com.przunk.kratkoza.domain.stats.Statistics
import com.przunk.kratkoza.domain.sudoku.DifficultyEvaluator
import com.przunk.kratkoza.domain.sudoku.PuzzleId
import com.przunk.kratkoza.domain.sudoku.SudokuBoard
import com.przunk.kratkoza.domain.sudoku.SudokuSolver
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val dailyPuzzleDao: DailyPuzzleDao,
    private val savedGameDao: SavedGameDao,
    private val historyDao: GameHistoryDao,
    private val solvedBoardDao: SolvedBoardDao,
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
        return openGame(puzzle)
    }

    /** Opens the history row project.md section 33 counts as a started game, and the save for it. */
    internal suspend fun openGame(puzzle: Puzzle, rankedEligible: Boolean = false): SavedGame {
        val now = clock()
        val historyId = historyDao.insert(historyEntry(puzzle, startedAt = now))

        val save = SavedGame(
            state = GameState.newGame(puzzle),
            startedAt = now,
            lastPlayedAt = now,
            elapsedMillis = 0,
            hintCount = 0,
            historyId = historyId,
            // project.md section 24: solo games have no public leaderboard. Only a Daily starts
            // eligible, and section 28 can take that away.
            rankedEligible = rankedEligible,
        )
        save(save)
        return save
    }


    /**
     * Opens a board somebody shared as a code.
     *
     * The board is validated rather than trusted: it has to have exactly one solution, and one a
     * player could actually reach by reasoning (project.md section 5). A board from outside has not
     * been through the generator that guarantees this, and handing someone an unsolvable grid would
     * waste an evening before they suspected the puzzle rather than themselves.
     *
     * Difficulty is graded here for the same reason — the code does not carry it, so the label
     * cannot disagree with the board.
     */
    suspend fun importSharedBoard(code: String): BoardImportResult {
        val decoded = when (val result = BoardCode.decode(code)) {
            is BoardCode.Result.Decoded -> result
            BoardCode.Result.NotABoardCode -> return BoardImportResult.NotABoardCode
            BoardCode.Result.Damaged -> return BoardImportResult.Damaged
        }

        // Solving and grading are pure CPU and must not run on the main thread.
        val puzzle = withContext(computationDispatcher) {
            val givens = decoded.givens
            if (!SudokuSolver.hasUniqueSolution(givens)) return@withContext null
            val rating = DifficultyEvaluator.evaluate(givens) ?: return@withContext null
            val solution = SudokuSolver.solve(givens) ?: return@withContext null

            Puzzle(
                id = PuzzleId.of(decoded.generatorVersion, givens, solution),
                generatorVersion = decoded.generatorVersion,
                difficulty = rating.difficulty,
                gameType = GameType.SOLO,
                givens = givens,
                solution = solution,
                // Not ours to know: the sender's seed does not travel, and inventing one would
                // claim this board is reproducible from a generator run that never happened here.
                seed = null,
            )
        } ?: return BoardImportResult.NotAValidPuzzle

        // Already playing this exact board — the same puzzle shared twice, or shared back.
        loadGame(puzzle.id)?.let {
            return BoardImportResult.Ready(
                puzzleId = puzzle.id,
                difficulty = it.state.puzzle.difficulty,
                alreadyStarted = true,
            )
        }

        puzzleDao.insert(puzzle.toEntity())
        openGame(puzzle)

        return BoardImportResult.Ready(
            puzzleId = puzzle.id,
            difficulty = puzzle.difficulty,
            alreadyStarted = false,
        )
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
                hintCount = game.hintCount,
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
                hintCount = save.hintCount,
                historyId = save.historyId,
                rankedEligible = save.rankedEligible,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * The instant after which this Daily can no longer produce a ranked result.
     *
     * The date comes from the persisted Daily mapping rather than from `now`: after the changeover
     * the current date describes the new board, not the saved board the player is resuming.
     */
    suspend fun dailyRankingDeadline(puzzleId: String): Long? =
        dailyPuzzleDao.dateOfPuzzle(puzzleId)
            ?.let(LocalDate::parse)
            ?.let(DailySchedule::endOf)
            ?.toEpochMilli()

    /**
     * Closes a game out: records the result and drops the save.
     *
     * [completed] is the caller's answer to "is the board correctly solved" — project.md section 16
     * is explicit that a game only counts as finished when it actually is.
     *
     * A game whose record was wiped by [resetStatistics] has no row to update, so it opens one now.
     * The game is then counted as both started and finished at this moment, which is the honest
     * reading: as far as the record is concerned, it began when the record did.
     */
    suspend fun finishGame(game: SavedGame, completed: Boolean): FinishedGame {
        val now = clock()
        // project.md section 32: XP is for finishing. An abandoned game earns none, and mistakes
        // take none away — the penalty for guessing belongs to the ranking (section 29), not here.
        val xp = if (completed) {
            PlayerProgress.xpForCompleting(game.state.puzzle.difficulty)
        } else {
            0
        }

        // Only a Daily is scored (project.md section 29). A solo game has nothing to be compared
        // against, and an unfinished board has no score at all — which is not the same as zero.
        val score = if (completed && game.state.puzzle.gameType == GameType.DAILY) {
            DailyScore.of(
                difficulty = game.state.puzzle.difficulty,
                elapsedMillis = game.elapsedMillis,
                mistakeCount = game.state.mistakeCount,
            )
        } else {
            null
        }

        val historyId = if (game.historyId == NO_HISTORY_ROW) {
            historyDao.insert(
                historyEntry(game.state.puzzle, startedAt = game.startedAt).copy(
                    finishedAt = now,
                    elapsedMillis = game.elapsedMillis,
                    mistakeCount = game.state.mistakeCount,
                    hintCount = game.hintCount,
                    completed = completed,
                    xpEarned = xp,
                    rankedEligible = game.rankedEligible,
                    score = score,
                ),
            )
        } else {
            historyDao.markFinished(
                id = game.historyId,
                finishedAt = now,
                elapsedMillis = game.elapsedMillis,
                mistakeCount = game.state.mistakeCount,
                hintCount = game.hintCount,
                completed = completed,
                xpEarned = xp,
                rankedEligible = game.rankedEligible,
                score = score,
            )
            game.historyId
        }

        // The archive is a record of boards beaten in ordinary play. Daily already has a results
        // screen and a ranking, and mixing it in here would make "History" another Daily screen.
        // A separate row per completion is intentional: replaying the same puzzle is a new memory,
        // while the list also reports how many archive rows share its puzzle id.
        if (completed && game.state.puzzle.gameType == GameType.SOLO) {
            solvedBoardDao.insert(
                SolvedBoardEntity(
                    puzzleId = game.state.puzzle.id,
                    completedAt = now,
                    elapsedMillis = game.elapsedMillis,
                    mistakeCount = game.state.mistakeCount,
                    hintCount = game.hintCount,
                    xpEarned = xp,
                    score = score,
                ),
            )
        }
        savedGameDao.delete(game.state.puzzle.id)

        return FinishedGame(
            historyId = historyId,
            gameType = game.state.puzzle.gameType,
            difficulty = game.state.puzzle.difficulty,
            score = score,
            rankedEligible = game.rankedEligible,
        )
    }


    /**
     * Records that a result actually reached the public ranking.
     *
     * Separate from finishing on purpose: the row has to be written before the network is involved,
     * so that a solved board survives a failed submission. This is the second, optional half.
     */
    suspend fun markPubliclyRanked(historyId: Long) = historyDao.markPubliclyRanked(historyId)

    /** Abandons a game. The history row stays open, so it still counts as started but not completed. */
    suspend fun deleteSave(puzzleId: String) = savedGameDao.delete(puzzleId)


    /** Unfinished games, most recently played first (project.md section 22). */
    val savedGames: Flow<List<SavedGameSummary>> =
        savedGameDao.observeAll().map { rows -> rows.mapNotNull(SavedGameSummaryRow::toSummary) }

    /** The game the Home screen offers to continue (project.md section 22). */
    val mostRecentSaveId: Flow<String?> = savedGameDao.observeMostRecentId()

    /** Successful Solo completions, independent of statistics and newest first. */
    val solvedBoards: Flow<List<SolvedBoard>> =
        solvedBoardDao.observeAll().map { rows -> rows.mapNotNull(SolvedBoardRow::toSolvedBoard) }

    /** One archived completion for its detail screen. */
    fun solvedBoard(id: Long): Flow<SolvedBoard?> =
        solvedBoardDao.observeById(id).map { it?.toSolvedBoard() }

    /** The same stable share code used by an active or saved game. */
    fun shareCodeFor(board: SolvedBoard): String =
        BoardCode.encode(board.givens, board.generatorVersion)

    /**
     * Opens a fresh attempt at an archived board, or resumes the one already on the board.
     *
     * The mutex closes the double-tap race: without it two concurrent calls can both observe no
     * save, open two history rows, and have the second save silently replace the first.
     */
    suspend fun replaySolvedBoard(puzzleId: String): SavedGame? = replayMutex.withLock {
        loadGame(puzzleId)?.let { return@withLock it }
        val puzzle = puzzleDao.byId(puzzleId)?.toPuzzle()
            ?.takeIf { it.gameType == GameType.SOLO }
            ?: return@withLock null
        openGame(puzzle)
    }

    private val replayMutex = Mutex()

}

/**
 * An open history row for [puzzle].
 *
 * `xpEarned` is filled in when the game finishes, not when it opens. `score` stays empty:
 * project.md section 23 wants it kept and section 39 leaves the algorithm open, so the column
 * exists and waits rather than holding a made-up number.
 */
private fun historyEntry(puzzle: Puzzle, startedAt: Long) = GameHistoryEntity(
    puzzleId = puzzle.id,
    gameType = puzzle.gameType.name,
    difficulty = puzzle.difficulty.name,
    startedAt = startedAt,
    finishedAt = null,
    elapsedMillis = 0,
    mistakeCount = 0,
    hintCount = 0,
    completed = false,
    score = null,
    xpEarned = 0,
    publiclyRanked = false,
    rankedEligible = false,
    autoRemoveCandidates = true,
    rulesetVersion = SudokuGenerator.GENERATOR_VERSION,
)

internal fun Puzzle.toEntity() = PuzzleEntity(
    puzzleId = id,
    generatorVersion = generatorVersion,
    difficulty = difficulty.name,
    gameType = gameType.name,
    givens = givens.toStorageString(),
    solution = solution.toStorageString(),
    seed = seed,
)

/** `null` when the stored row cannot be read as a puzzle; the caller treats that as "not there". */
internal fun PuzzleEntity.toPuzzle(): Puzzle? = try {
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
        solution = SudokuBoard.parse(solution),
        generatorVersion = generatorVersion,
    )
} catch (_: IllegalArgumentException) {
    null
}

private fun SolvedBoardRow.toSolvedBoard(): SolvedBoard? = try {
    SolvedBoard(
        id = id,
        puzzleId = puzzleId,
        difficulty = Difficulty.fromStorageName(difficulty) ?: return null,
        givens = SudokuBoard.parse(givens),
        generatorVersion = generatorVersion,
        completedAt = completedAt,
        elapsedMillis = elapsedMillis,
        mistakeCount = mistakeCount,
        hintCount = hintCount,
        xpEarned = xpEarned,
        score = score,
        completionCount = completionCount,
        inProgress = inProgress,
        imported = seed == null,
    )
} catch (_: IllegalArgumentException) {
    null
}
