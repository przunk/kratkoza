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
 * Highlight tiers are ordered by strength so they stack readably:
 * selected cell > same digit > peer (row/column/block) > plain surface.
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

internal val LightBoardColors = BoardColors(
    surface = Neutral100,
    gridLine = Neutral80,
    blockLine = Neutral30,
    selectedCell = HighlightLightSelected,
    peerCell = HighlightLightPeer,
    sameDigitCell = HighlightLightSameDigit,
    errorCell = Error90,
    givenDigit = Neutral30,
    enteredDigit = EnteredLight,
    errorDigit = Error40,
    candidate = Neutral50,
    autoHintDigit = Neutral60,
)

internal val DarkBoardColors = BoardColors(
    surface = Neutral12,
    gridLine = Neutral30,
    blockLine = Neutral60,
    selectedCell = HighlightDarkSelected,
    peerCell = HighlightDarkPeer,
    sameDigitCell = HighlightDarkSameDigit,
    errorCell = Error20,
    givenDigit = Neutral80,
    enteredDigit = EnteredDark,
    errorDigit = Error80,
    candidate = Neutral60,
    autoHintDigit = Neutral50,
)

/**
 * No sensible default exists outside [KratkozaTheme]; a board rendered without the theme is a bug,
 * so this fails loudly instead of silently painting the wrong colours.
 */
internal val LocalBoardColors = staticCompositionLocalOf<BoardColors> {
    error("BoardColors requested outside of KratkozaTheme")
}
