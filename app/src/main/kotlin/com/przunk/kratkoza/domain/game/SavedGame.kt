package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.sudoku.SudokuBoard

/**
 * A game that can be resumed (project.md section 22): the board state plus the bookkeeping that
 * lives outside [GameState].
 *
 * [historyId] points at the history row opened when the game started, so finishing or abandoning
 * this game updates that record instead of creating a second one.
 */
data class SavedGame(
    val state: GameState,
    val startedAt: Long,
    val lastPlayedAt: Long,
    /** Time actually played, pauses excluded (project.md section 20). */
    val elapsedMillis: Long,
    /** The record row this game belongs to, or [NO_HISTORY_ROW] after the record was reset. */
    val historyId: Long,
    /** project.md section 28. Always false while only solo games exist. */
    val rankedEligible: Boolean,
)

/**
 * No record row: the statistics were reset while this game was still on the board. Room's generated
 * ids start at 1, so this can never collide with a real row.
 */
const val NO_HISTORY_ROW = 0L

/**
 * A row of the saved-games list (project.md section 22).
 *
 * Carries the board itself because the list shows a small preview of it, and [givens] alongside so
 * the preview can tell the player's digits from the starting ones.
 */
data class SavedGameSummary(
    val puzzleId: String,
    val difficulty: Difficulty,
    val gameType: GameType,
    val startedAt: Long,
    val lastPlayedAt: Long,
    val elapsedMillis: Long,
    val mistakeCount: Int,
    val board: SudokuBoard,
    val givens: SudokuBoard,
)
