package com.przunk.kratkoza.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.sudoku.Sudoku

/** An in-memory database, thrown away with the test that made it. */
internal fun createTestDatabase(): KratkozaDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        KratkozaDatabase::class.java,
    ).allowMainThreadQueries().build()

/** A puzzle row with sensible defaults; every field can be overridden where a test cares. */
internal fun puzzleEntity(
    puzzleId: String,
    difficulty: Difficulty = Difficulty.NORMAL,
    gameType: GameType = GameType.SOLO,
): PuzzleEntity = PuzzleEntity(
    puzzleId = puzzleId,
    generatorVersion = 1,
    difficulty = difficulty.name,
    gameType = gameType.name,
    givens = ".".repeat(Sudoku.CELL_COUNT),
    solution = "1".repeat(Sudoku.CELL_COUNT),
    seed = null,
)

internal fun savedGameEntity(
    puzzleId: String,
    historyId: Long,
    lastPlayedAt: Long = 0,
    elapsedMillis: Long = 0,
    mistakeCount: Int = 0,
): SavedGameEntity = SavedGameEntity(
    puzzleId = puzzleId,
    historyId = historyId,
    startedAt = 0,
    lastPlayedAt = lastPlayedAt,
    elapsedMillis = elapsedMillis,
    board = ".".repeat(Sudoku.CELL_COUNT),
    candidates = "000".repeat(Sudoku.CELL_COUNT),
    undoHistory = "",
    mistakeCount = mistakeCount,
    rankedEligible = false,
)

internal fun historyEntity(
    puzzleId: String,
    difficulty: Difficulty = Difficulty.NORMAL,
    completed: Boolean = false,
    elapsedMillis: Long = 0,
    mistakeCount: Int = 0,
    xpEarned: Int = 0,
    startedAt: Long = 0,
): GameHistoryEntity = GameHistoryEntity(
    puzzleId = puzzleId,
    gameType = GameType.SOLO.name,
    difficulty = difficulty.name,
    startedAt = startedAt,
    finishedAt = if (completed) startedAt + elapsedMillis else null,
    elapsedMillis = elapsedMillis,
    mistakeCount = mistakeCount,
    completed = completed,
    score = null,
    xpEarned = xpEarned,
    publiclyRanked = false,
    autoRemoveCandidates = true,
    rulesetVersion = 1,
)
