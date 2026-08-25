package com.przunk.kratkoza.navigation

/**
 * The screens of the app (technical_requirements.md section 11).
 *
 * Plain string routes rather than the type-safe Navigation API, which would pull in
 * kotlinx-serialization for the sake of one route argument. technical_requirements.md section 9 asks
 * a dependency to earn its place, and one string does not earn it.
 *
 * Daily and Challenge are absent: nothing about this file makes them harder to add.
 */
object Destinations {
    const val HOME = "home"
    const val NEW_GAME = "new_game"
    const val SAVED_GAMES = "saved_games"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"

    private const val GAME_BASE = "game"
    const val GAME_PUZZLE_ID = "puzzleId"

    /** `game/{puzzleId}` — the board to open, always one already stored. */
    const val GAME = "$GAME_BASE/{$GAME_PUZZLE_ID}"

    fun game(puzzleId: String): String = "$GAME_BASE/$puzzleId"
}
