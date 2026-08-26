package com.przunk.kratkoza.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.przunk.kratkoza.domain.settings.BoardPalette
import kotlin.math.abs
import kotlin.math.cbrt
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

    /** CIE L*, from the relative luminance Compose exposes. */
    private fun Color.lightness(): Float {
        val y = luminance()
        val f = if (y > LAB_EPSILON) cbrt(y.toDouble()).toFloat() else LAB_KAPPA * y + LAB_OFFSET
        return 116f * f - 16f
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
        for (case in cases) {
            val colors = case.colors()
            val surface = colors.surface.lightness()
            val faintest = abs(colors.peerCell.lightness() - surface)

            assertTrue("$case: the peer tier is invisible", faintest > MINIMUM_TIER_SEPARATION)
        }
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

                val plainSpread = abs(plain.selectedCell.lightness() - plain.surface.lightness())
                val strongSpread = abs(strong.selectedCell.lightness() - strong.surface.lightness())

                assertTrue(
                    "$palette dark=$dark: high contrast flattened the board",
                    strongSpread >= plainSpread,
                )
            }
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
 * Below this a tier is a rounding error rather than a highlight. Five points of L* is roughly where
 * a large block of colour stops being reliably distinguishable from its neighbour.
 */
private const val MINIMUM_TIER_SEPARATION = 5f

/** A digit closer than this to its background is not readable at board size. */
private const val MINIMUM_DIGIT_SEPARATION = 25f

private const val LAB_EPSILON = 0.008856f
private const val LAB_KAPPA = 7.787f
private const val LAB_OFFSET = 16f / 116f
