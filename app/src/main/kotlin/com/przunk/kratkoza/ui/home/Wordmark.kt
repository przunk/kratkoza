package com.przunk.kratkoza.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation

/**
 * The app's name as geometry rather than as text.
 *
 * Drawn rather than set in a typeface for the same reason the icons are: it belongs to the artwork,
 * has to survive both themes and high contrast, and must not resize with the system font — inside a
 * moving scene a wordmark that reflows is a wordmark that breaks the composition. The letters are
 * original geometric capitals: flat terminals, one stroke weight, counters cut rather than drawn.
 *
 * Each glyph is described on a box one unit tall and its own width wide, as solids that are unioned
 * and holes that are then subtracted. Two lists rather than one even-odd path, because K, A and R
 * overlap their own strokes and even-odd would punch those overlaps into gaps.
 */
internal object Wordmark {

    const val TEXT = "KRATKOZA"

    /** Stroke weight, as a fraction of cap height. */
    private const val STROKE = 0.25f

    /** Space between letters, as a fraction of cap height. */
    private const val TRACKING = 0.15f

    private val WIDTHS = mapOf(
        'K' to 0.80f, 'R' to 0.86f, 'A' to 1.00f,
        'T' to 0.82f, 'O' to 0.90f, 'Z' to 0.78f,
    )

    /** Width of the whole word at cap height 1, so a caller can scale it to fit a box. */
    val advance: Float =
        TEXT.sumOf { WIDTHS.getValue(it).toDouble() }.toFloat() + TRACKING * (TEXT.length - 1)

    /**
     * The word as one path, cap-height [cap], starting at the origin.
     *
     * Holes are removed with [PathOperation.Difference] rather than by drawing over them, so the
     * result is a single shape a caller can fill, offset or reuse for a shadow.
     */
    fun path(cap: Float): Path {
        val whole = Path()
        var pen = 0f
        for (character in TEXT) {
            val width = WIDTHS.getValue(character)
            whole.addPath(glyph(character, width * cap, cap, pen))
            pen += (width + TRACKING) * cap
        }
        return whole
    }

    private fun glyph(character: Char, w: Float, cap: Float, x: Float): Path {
        val t = STROKE * cap
        val solids = Path()
        val holes = Path()

        when (character) {
            'K' -> {
                solids.bar(x, 0f, x + t, cap)
                solids.slab(x + t * 0.9f, 0.54f * cap, x + w, -0.02f * cap, t * 0.5f)
                solids.slab(x + t * 0.9f, 0.46f * cap, x + w, 1.02f * cap, t * 0.5f)
            }

            'R' -> {
                // The bowl has to be deeper than two strokes or its counter comes out negative and
                // reads as a slot rather than as a hole.
                val right = x + w - t * 0.1f
                val bottom = 0.64f * cap
                val waist = t * 0.82f
                solids.bar(x, 0f, x + t, cap)
                solids.bar(x, 0f, right, t)
                solids.bar(x, bottom - waist, right, bottom)
                solids.bar(right - t, 0f, right, bottom)
                solids.slab(x + (right - x) * 0.60f, bottom - t * 0.2f, x + w, 1.02f * cap, t * 0.5f)
                holes.bar(x + t, t, right - t, bottom - waist)
            }

            'A' -> {
                // A flat top wider than one stroke. Narrower and the legs overlap up there, which
                // closes the counter until halfway down the letter.
                val apex = t * 1.15f
                val cross = 0.64f * cap
                solids.slab(x + w / 2 - apex / 2, 0f, x + t * 0.5f, cap, t * 0.5f)
                solids.slab(x + w / 2 + apex / 2, 0f, x + w - t * 0.5f, cap, t * 0.5f)
                solids.bar(x + t * 0.62f, cross, x + w - t * 0.62f, cross + t * 0.70f)
            }

            'T' -> {
                solids.bar(x, 0f, x + w, t)
                solids.bar(x + w / 2 - t / 2, 0f, x + w / 2 + t / 2, cap)
            }

            'O' -> {
                solids.addOval(Rect(x, 0f, x + w, cap))
                holes.addOval(Rect(x + t, t, x + w - t, cap - t))
            }

            'Z' -> {
                solids.bar(x, 0f, x + w, t)
                solids.bar(x, cap - t, x + w, cap)
                solids.slab(x + w - t * 0.55f, t * 0.85f, x + t * 0.55f, cap - t * 0.85f, t * 0.6f)
            }

            else -> error("The wordmark has no glyph for '$character'")
        }

        return if (holes.isEmpty) solids else Path().apply { op(solids, holes, PathOperation.Difference) }
    }

    private fun Path.bar(x0: Float, y0: Float, x1: Float, y1: Float) {
        addRect(Rect(x0, y0, x1, y1))
    }

    /** A thick bar from one point to another, [half] wide either side, measured horizontally. */
    private fun Path.slab(ax: Float, ay: Float, bx: Float, by: Float, half: Float) {
        moveTo(ax - half, ay)
        lineTo(ax + half, ay)
        lineTo(bx + half, by)
        lineTo(bx - half, by)
        close()
    }
}

/** The size the wordmark occupies when fitted to [width]. */
internal fun wordmarkSize(width: Float): Size =
    Size(width, width / Wordmark.advance)
