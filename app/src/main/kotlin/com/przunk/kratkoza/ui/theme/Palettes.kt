package com.przunk.kratkoza.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.przunk.kratkoza.domain.settings.BoardPalette

/**
 * Turns a [BoardPalette] into the colours the board and the accents are drawn in.
 *
 * Each palette is written out for light and for dark rather than derived from a seed: the board is
 * looked at for a long time, and a generated ramp lands on muddy greens and bruised purples often
 * enough that hand-picking is worth the extra lines.
 *
 * Hand-picked, but not by eye alone. `PalettesTest` measures every tier in CIE L*: the peer fill
 * needs a deliberate step from the surface, and every adjacent highlight needs another visible
 * step. Checking both matters because correctly ordered colours can still collapse into one wash.
 *
 * High contrast is computed from that base without replacing its hue. See [withHigherContrast].
 */
internal object Palettes {

    fun boardColors(
        palette: BoardPalette,
        dark: Boolean,
        highContrast: Boolean,
    ): BoardColors {
        val base = if (dark) darkColors(palette) else lightColors(palette)
        return if (highContrast) base.withHigherContrast(dark) else base
    }

    /** The hue the rest of the interface borrows, so the app does not fight the board. */
    fun accent(palette: BoardPalette, dark: Boolean): Color = when (palette) {
        BoardPalette.SLATE -> if (dark) SlateBlue80 else SlateBlue40
        BoardPalette.SEPIA -> if (dark) SepiaAccentDark else SepiaAccentLight
        BoardPalette.FOREST -> if (dark) ForestAccentDark else ForestAccentLight
        BoardPalette.GRAPHITE -> if (dark) GraphiteAccentDark else GraphiteAccentLight
        BoardPalette.HONEY -> if (dark) HoneyAccentDark else HoneyAccentLight
    }

    private fun lightColors(palette: BoardPalette): BoardColors = when (palette) {
        BoardPalette.SLATE -> BoardColors(
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

        BoardPalette.SEPIA -> BoardColors(
            surface = Color(0xFFFBF7F0),
            gridLine = Color(0xFFD8CDB8),
            blockLine = Color(0xFF7A6B52),
            selectedCell = Color(0xFFCAB895),
            peerCell = Color(0xFFE6DFD1),
            sameDigitCell = Color(0xFFD5CCB8),
            errorCell = Error90,
            givenDigit = Color(0xFF4A4034),
            enteredDigit = SepiaAccentLight,
            errorDigit = Error40,
            candidate = Color(0xFF938976),
            autoHintDigit = Color(0xFFA79C87),
        )

        BoardPalette.FOREST -> BoardColors(
            surface = Color(0xFFF8FBF8),
            gridLine = Color(0xFFC6D5C8),
            blockLine = Color(0xFF445747),
            selectedCell = Color(0xFFA6C4AD),
            peerCell = Color(0xFFDDE4DE),
            sameDigitCell = Color(0xFFC6D3C8),
            errorCell = Error90,
            givenDigit = Color(0xFF2C3830),
            enteredDigit = ForestAccentLight,
            errorDigit = Error40,
            candidate = Color(0xFF6E7F72),
            autoHintDigit = Color(0xFF8B9A8E),
        )

        BoardPalette.GRAPHITE -> BoardColors(
            surface = Neutral100,
            gridLine = Color(0xFFCBCBCB),
            blockLine = Color(0xFF3A3A3A),
            selectedCell = Color(0xFFC1C1C1),
            peerCell = Color(0xFFE7E7E7),
            sameDigitCell = Color(0xFFD4D4D4),
            errorCell = Error90,
            givenDigit = Color(0xFF5A5A5A),
            // In a palette with no hue, the given/entered difference has to be lightness. The
            // entered digit is the darker one, because it is the one the player put there.
            enteredDigit = Color(0xFF111111),
            errorDigit = Error40,
            candidate = Color(0xFF8A8A8A),
            autoHintDigit = Color(0xFF9C9C9C),
        )

        // Tiers chosen in CIE L* with headroom over PalettesTest: peer 9.3 from the surface, then
        // steps of 7.8 and 8.0. The accent clears 5.7:1 against the white label of a filled button.
        BoardPalette.HONEY -> BoardColors(
            surface = Color(0xFFFBF8EE),
            gridLine = Color(0xFFE3DAC2),
            blockLine = Color(0xFF857652),
            selectedCell = Color(0xFFCFAE55),
            peerCell = Color(0xFFEADDB4),
            sameDigitCell = Color(0xFFDCC686),
            errorCell = Error90,
            givenDigit = Color(0xFF4A4232),
            enteredDigit = HoneyAccentLight,
            errorDigit = Error40,
            candidate = Color(0xFF958A6F),
            autoHintDigit = Color(0xFFAEA488),
        )
    }

    private fun darkColors(palette: BoardPalette): BoardColors = when (palette) {
        BoardPalette.SLATE -> BoardColors(
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

        BoardPalette.SEPIA -> BoardColors(
            surface = Color(0xFF17130E),
            gridLine = Color(0xFF3A3226),
            blockLine = Color(0xFF7C6F58),
            selectedCell = Color(0xFF63533A),
            peerCell = Color(0xFF302921),
            sameDigitCell = Color(0xFF473C30),
            errorCell = Error20,
            givenDigit = Color(0xFFD9CFBE),
            enteredDigit = SepiaAccentDark,
            errorDigit = Error80,
            candidate = Color(0xFF8C8371),
            autoHintDigit = Color(0xFF6F6858),
        )

        BoardPalette.FOREST -> BoardColors(
            surface = Color(0xFF101512),
            gridLine = Color(0xFF2A342C),
            blockLine = Color(0xFF6A7D6E),
            selectedCell = Color(0xFF375D45),
            peerCell = Color(0xFF242C26),
            sameDigitCell = Color(0xFF364137),
            errorCell = Error20,
            givenDigit = Color(0xFFD3DED5),
            enteredDigit = ForestAccentDark,
            errorDigit = Error80,
            candidate = Color(0xFF7E8F82),
            autoHintDigit = Color(0xFF63715F),
        )

        BoardPalette.GRAPHITE -> BoardColors(
            surface = Color(0xFF101010),
            gridLine = Color(0xFF2E2E2E),
            blockLine = Color(0xFF7A7A7A),
            selectedCell = Color(0xFF525252),
            peerCell = Color(0xFF272727),
            sameDigitCell = Color(0xFF3B3B3B),
            errorCell = Error20,
            givenDigit = Color(0xFF9E9E9E),
            enteredDigit = Color(0xFFF4F4F4),
            errorDigit = Error80,
            candidate = Color(0xFF7A7A7A),
            autoHintDigit = Color(0xFF616161),
        )

        // Deliberately flat, for play in a dark room (2026-11-12): digits at about 6.7:1 against the
        // surface instead of the 12:1 the other dark palettes reach, still above WCAG AA, and block
        // lines dimmed with them. The highlight tiers sit just above the dark-display floor in
        // PalettesTest — peer 10.8 L* from the surface, then steps of 8.8 and 8.2 — because that
        // floor came from a device review and is the one thing flattening must not cross.
        BoardPalette.HONEY -> BoardColors(
            surface = Color(0xFF16140E),
            gridLine = Color(0xFF2E2A1C),
            blockLine = Color(0xFF5E5642),
            selectedCell = Color(0xFF5C4F2E),
            peerCell = Color(0xFF2F2A1A),
            sameDigitCell = Color(0xFF453D25),
            errorCell = Error20,
            givenDigit = Color(0xFFA39A85),
            enteredDigit = HoneyAccentDark,
            errorDigit = Error80,
            candidate = Color(0xFF756D5C),
            autoHintDigit = Color(0xFF5A5446),
        )
    }
}

/**
 * A stronger version of a palette (project.md sections 9 and 35).
 *
 * Highlight fills stay untouched. Rebuilding them as fractions between the surface and white made
 * Slate, Sepia and Forest collapse into the same neutral-grey ramp — exactly the colour information
 * a palette is meant to retain. Moving only the ground away from those fills increases their
 * contrast while preserving both their hue and their carefully measured adjacent spacing.
 *
 * "Higher contrast, still elegant" is what section 9 asks for, so the surface stops short of pure
 * white or pure black: paper-white behind nine black digits glares under a lamp, and the difference
 * between 0xFF and 0xF7 costs nothing legible.
 */
private fun BoardColors.withHigherContrast(dark: Boolean): BoardColors {
    val ink = if (dark) Color.White else Color.Black

    return copy(
        surface = lerp(surface, if (dark) Color.Black else Color.White, SURFACE_PUSH),
        gridLine = lerp(surface, ink, GRID_LINE_STEP),
        blockLine = lerp(surface, ink, BLOCK_LINE_STEP),
        // Digits go the other way — towards the ink, not away from the surface — because their job
        // is to be read, not to sit at a measured distance from anything.
        givenDigit = lerp(givenDigit, ink, DIGIT_PUSH),
        enteredDigit = lerp(enteredDigit, ink, ACCENT_PUSH),
        candidate = lerp(candidate, ink, DIGIT_PUSH),
        errorDigit = lerp(errorDigit, ink, ACCENT_PUSH),
        errorCell = lerp(errorCell, if (dark) Color.Black else Color.White, ERROR_CELL_PUSH),
        // The hint stays deliberately faint even here: project.md section 17 requires it to be
        // unmistakably different from a digit the player entered, and making it bold would blur
        // exactly the line it has to hold.
        autoHintDigit = lerp(autoHintDigit, ink, HINT_PUSH),
    )
}

private const val SURFACE_PUSH = 0.30f
private const val GRID_LINE_STEP = 0.30f
private const val BLOCK_LINE_STEP = 0.78f
private const val DIGIT_PUSH = 0.45f
private const val ACCENT_PUSH = 0.25f
private const val ERROR_CELL_PUSH = 0.20f
private const val HINT_PUSH = 0.10f
