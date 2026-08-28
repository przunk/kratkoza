package com.przunk.kratkoza.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.przunk.kratkoza.domain.settings.BoardPalette
import kotlin.math.abs
import kotlin.math.cbrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule every palette has to obey: the highlight tiers separate by *lightness*, in that order,
 * so the board is legible without telling any two hues apart (project.md sections 8 and 10).
 *
 * Worth a test rather than a comment because it is the thing that quietly breaks. A fifth palette
 * added later, or one tier nudged by a shade, looks fine in the file and turns the board to mud on
 * a phone — and nothing else here would notice. It has already earned its keep once: the sepia and
 * forest light tiers went in at roughly half the separation of slate's and were caught here.
 *
 * Measured in CIE L*, not in the relative luminance Compose reports. Luminance is photometric and
 * compresses hard near black — two dark greys a person can easily tell apart differ by less than
 * 0.01 of it, so a threshold that means anything in a light theme means nothing in a dark one. L*
 * is perceptually even, and one threshold then holds for both.
 */
class PalettesTest {

    private data class Case(val palette: BoardPalette, val dark: Boolean, val highContrast: Boolean)

    private val cases: List<Case> = BoardPalette.entries.flatMap { palette ->
        listOf(true, false).flatMap { dark ->
            listOf(true, false).map { highContrast -> Case(palette, dark, highContrast) }
        }
    }

    private fun Case.colors() = Palettes.boardColors(palette, dark, highContrast)

    private data class TierGaps(
        val surfaceToPeer: Float,
        val peerToSameDigit: Float,
        val sameDigitToSelected: Float,
    )

    /** CIE L*, from the relative luminance Compose exposes. */
    private fun Color.lightness(): Float {
        val y = luminance()
        val f = if (y > LAB_EPSILON) cbrt(y.toDouble()).toFloat() else LAB_KAPPA * y + LAB_OFFSET
        return 116f * f - 16f
    }

    private fun BoardColors.tierGaps(): TierGaps {
        val surfaceDistance = { color: Color -> abs(color.lightness() - surface.lightness()) }
        val peer = surfaceDistance(peerCell)
        val sameDigit = surfaceDistance(sameDigitCell)
        val selected = surfaceDistance(selectedCell)
        return TierGaps(
            surfaceToPeer = peer,
            peerToSameDigit = sameDigit - peer,
            sameDigitToSelected = selected - sameDigit,
        )
    }

    @Test
    fun `tiers move away from the surface in order`() {
        for (case in cases) {
            val colors = case.colors()
            // In a light theme each tier is darker than the last; in a dark theme, lighter. Measured
            // as distance from the surface, both directions read the same way.
            val surface = colors.surface.lightness()
            val peer = abs(colors.peerCell.lightness() - surface)
            val sameDigit = abs(colors.sameDigitCell.lightness() - surface)
            val selected = abs(colors.selectedCell.lightness() - surface)

            assertTrue("$case: matching digits must stand out more than peers", sameDigit > peer)
            assertTrue("$case: the selection must stand out most", selected > sameDigit)
        }
    }

    @Test
    fun `every tier is actually visible against the surface`() {
        val violations = cases.mapNotNull { case ->
            val colors = case.colors()
            val surface = colors.surface.lightness()
            val faintest = abs(colors.peerCell.lightness() - surface)

            if (faintest < MINIMUM_SURFACE_TO_PEER_SEPARATION) {
                "$case: peer is only $faintest L* away from the surface"
            } else {
                null
            }
        }

        assertTrue(violations.joinToString(separator = "\n"), violations.isEmpty())
    }

    @Test
    fun `adjacent highlight tiers stay visibly apart`() {
        val violations = cases.flatMap { case ->
            val colors = case.colors()
            val surface = colors.surface.lightness()
            val peer = abs(colors.peerCell.lightness() - surface)
            val sameDigit = abs(colors.sameDigitCell.lightness() - surface)
            val selected = abs(colors.selectedCell.lightness() - surface)

            listOf(
                "peer to same-digit" to sameDigit - peer,
                "same-digit to selected" to selected - sameDigit,
            ).mapNotNull { (name, gap) ->
                if (gap < MINIMUM_ADJACENT_TIER_SEPARATION) {
                    "$case: $name gap is only $gap L* " +
                        "(surface distances: peer=$peer, same-digit=$sameDigit, selected=$selected)"
                } else {
                    null
                }
            }
        }

        assertTrue(violations.joinToString(separator = "\n"), violations.isEmpty())
    }

    @Test
    fun `normal dark tiers keep extra headroom for a dim display`() {
        val violations = BoardPalette.entries.flatMap { palette ->
            val gaps = Palettes.boardColors(palette, dark = true, highContrast = false).tierGaps()
            listOf(
                "surface to peer" to
                    (gaps.surfaceToPeer to MINIMUM_DARK_SURFACE_TO_PEER_SEPARATION),
                "peer to same-digit" to
                    (gaps.peerToSameDigit to MINIMUM_DARK_ADJACENT_TIER_SEPARATION),
                "same-digit to selected" to
                    (gaps.sameDigitToSelected to MINIMUM_DARK_ADJACENT_TIER_SEPARATION),
            ).mapNotNull { (name, values) ->
                if (values.first < values.second) {
                    "$palette: dark $name gap is only ${values.first} L*, expected ${values.second}"
                } else {
                    null
                }
            }
        }

        assertTrue(violations.joinToString(separator = "\n"), violations.isEmpty())
    }

    @Test
    fun `digits separate from the surface they sit on`() {
        for (case in cases) {
            val colors = case.colors()
            val surface = colors.surface.lightness()

            for ((name, color) in listOf(
                "given" to colors.givenDigit,
                "entered" to colors.enteredDigit,
                "candidate" to colors.candidate,
            )) {
                assertTrue(
                    "$case: the $name digit does not separate from the surface",
                    abs(color.lightness() - surface) > MINIMUM_DIGIT_SEPARATION,
                )
            }
        }
    }

    @Test
    fun `high contrast never reduces separation`() {
        // The whole point of the option. A palette whose high-contrast variant is flatter than its
        // ordinary one would be worse than not offering it.
        for (palette in BoardPalette.entries) {
            for (dark in listOf(true, false)) {
                val plain = Palettes.boardColors(palette, dark, highContrast = false)
                val strong = Palettes.boardColors(palette, dark, highContrast = true)
                val plainGaps = plain.tierGaps()
                val strongGaps = strong.tierGaps()

                for ((name, gaps) in listOf(
                    "surface to peer" to (plainGaps.surfaceToPeer to strongGaps.surfaceToPeer),
                    "peer to same-digit" to
                        (plainGaps.peerToSameDigit to strongGaps.peerToSameDigit),
                    "same-digit to selected" to
                        (plainGaps.sameDigitToSelected to strongGaps.sameDigitToSelected),
                )) {
                    assertTrue(
                        "$palette dark=$dark: high contrast reduced $name from " +
                            "${gaps.first} to ${gaps.second} L*",
                        gaps.second + LIGHTNESS_COMPARISON_EPSILON >= gaps.first,
                    )
                }
            }
        }
    }

    @Test
    fun `high contrast preserves each palettes highlight colours`() {
        for (palette in BoardPalette.entries) {
            for (dark in listOf(true, false)) {
                val plain = Palettes.boardColors(palette, dark, highContrast = false)
                val strong = Palettes.boardColors(palette, dark, highContrast = true)

                assertEquals("$palette dark=$dark: peer hue changed", plain.peerCell, strong.peerCell)
                assertEquals(
                    "$palette dark=$dark: same-digit hue changed",
                    plain.sameDigitCell,
                    strong.sameDigitCell,
                )
                assertEquals(
                    "$palette dark=$dark: selected hue changed",
                    plain.selectedCell,
                    strong.selectedCell,
                )
            }
        }
    }

    @Test
    fun `high contrast keeps control containers distinct from the screen`() {
        for (dark in listOf(true, false)) {
            val accent = Palettes.accent(BoardPalette.SLATE, dark)
            val plain = colorScheme(dark, accent, highContrast = false)
            val strong = colorScheme(dark, accent, highContrast = true)
            val plainGap = abs(
                plain.surfaceContainerHigh.lightness() - plain.background.lightness(),
            )
            val strongGap = abs(
                strong.surfaceContainerHigh.lightness() - strong.background.lightness(),
            )

            assertTrue(
                "dark=$dark: high contrast reduced control edge from $plainGap to $strongGap L*",
                strongGap >= plainGap,
            )
        }
    }

    @Test
    fun `the hint stays fainter than an entered digit`() {
        // project.md section 17: the 8-of-9 hint has to stay unmistakably apart from what the
        // player wrote, including in high contrast, where everything else is pushed harder.
        for (case in cases) {
            val colors = case.colors()
            val surface = colors.surface.lightness()

            assertTrue(
                "$case: the hint is as loud as an entered digit",
                abs(colors.autoHintDigit.lightness() - surface) <
                    abs(colors.enteredDigit.lightness() - surface),
            )
        }
    }
}

/**
 * A large area becomes noticeable at roughly five points of L*. Eight leaves enough headroom for
 * a dim phone, glare and the fact that the highlight is seen between dark grid lines.
 */
private const val MINIMUM_SURFACE_TO_PEER_SEPARATION = 8f

/**
 * Adjacent fills need more than a just-noticeable difference to remain distinct at cell size. Six
 * points also keep the minimum selected spread around 20 L*, below the digit threshold, so normal
 * mode does not become a heat map merely to satisfy the test.
 */
private const val MINIMUM_ADJACENT_TIER_SEPARATION = 6f

/** Device review showed that the general floor still disappears into a dark phone display. */
private const val MINIMUM_DARK_SURFACE_TO_PEER_SEPARATION = 10f
private const val MINIMUM_DARK_ADJACENT_TIER_SEPARATION = 8f

/** Subtraction from a changed surface can differ by a few float ULPs for otherwise equal gaps. */
private const val LIGHTNESS_COMPARISON_EPSILON = 0.001f

/** A digit closer than this to its background is not readable at board size. */
private const val MINIMUM_DIGIT_SEPARATION = 25f

private const val LAB_EPSILON = 0.008856f
private const val LAB_KAPPA = 7.787f
private const val LAB_OFFSET = 16f / 116f
