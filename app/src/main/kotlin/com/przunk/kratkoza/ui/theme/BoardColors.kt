package com.przunk.kratkoza.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours the sudoku board needs that Material 3 does not model: the several highlight tiers, the
 * given/entered digit distinction, and the grid lines.
 *
 * These are deliberately kept out of [androidx.compose.material3.ColorScheme]. Overloading M3 roles
 * (using `tertiaryContainer` as "peer highlight", say) makes both the board and the rest of the UI
 * harder to retheme later.
 *
 * Three background tiers, ordered by strength: selected cell > matching digits > peer
 * (row/column/block) > plain surface. They separate by *lightness*, in even steps, so the order is
 * legible without telling any two hues apart.
 *
 * The values for each palette live in [Palettes], which is also where the high-contrast variant is
 * computed.
 *
 * Matching digits (project.md section 10) took three tries to place. A tint too near the peer tint
 * was invisible; bolding the digits looked restless; giving them their own colour put a third
 * colour on a board that already carries grey, blue and red, and the result was hard to look at.
 * A quiet step in the same grey ramp says "these ones too" and then gets out of the way — which is
 * all section 10 asks of it.
 *
 * Digit colour therefore says only what it always said: given, entered, or wrong. Weight repeats
 * the given/entered part, so nothing rests on hue alone (project.md section 8).
 *
 * The tiers separate by lightness, not by hue — each step is a visibly different grey carrying a
 * trace of the accent. A reader who cannot tell the hues apart still sees three distinct steps, and
 * the board keeps looking like paper rather than a heat map (project.md sections 8 and 10).
 *
 * A given is drawn in a quieter grey than a player's digit, which is the opposite of the usual
 * "bold black givens". The board starts full of givens and fills up with the player's own answers;
 * making the answers the loud ones is what lets progress be read at a glance. Weight separates them
 * as well as colour, so the distinction does not rest on hue (project.md section 8).
 */
@Immutable
data class BoardColors(
    val surface: Color,
    val gridLine: Color,
    val blockLine: Color,
    val selectedCell: Color,
    val peerCell: Color,
    val sameDigitCell: Color,
    val errorCell: Color,
    val givenDigit: Color,
    val enteredDigit: Color,
    val errorDigit: Color,
    val candidate: Color,
    val autoHintDigit: Color,
)

/**
 * No sensible default exists outside [KratkozaTheme]; a board rendered without the theme is a bug,
 * so this fails loudly instead of silently painting the wrong colours.
 */
internal val LocalBoardColors = staticCompositionLocalOf<BoardColors> {
    error("BoardColors requested outside of KratkozaTheme")
}
