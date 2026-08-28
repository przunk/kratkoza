package com.przunk.kratkoza.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation

/**
 * The app's name as geometry rather than as text.
 *
 * Drawn rather than set in a typeface for the same reason the icons are: it belongs to the artwork,
 * has to survive both themes and high contrast, and must not resize with the system font — inside a
 * moving scene a wordmark that reflows is a wordmark that breaks the composition. The letters are
 * original geometric capitals: flat terminals, one stroke weight, counters cut rather than drawn.
 *
 * Each glyph is described on a box one unit tall and its own width wide, as pieces that are unioned
 * and holes that are then subtracted.
 *
 * The pieces are combined with an explicit union rather than added to one path. Compose fills with
 * the non-zero rule, which cancels where two shapes wind in opposite directions — and a diagonal
 * drawn upwards winds the other way from a rectangle drawn downwards. That is exactly where K, A and
 * Z overlap their own strokes, so those overlaps came out as holes: the letters looked as though
 * they had been XOR-ed. A union does not care which way a shape was drawn.
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
        val pieces = mutableListOf<Path>()
        val holes = mutableListOf<Path>()
        fun solid(build: Path.() -> Unit) = pieces.add(Path().apply(build))
        fun hole(build: Path.() -> Unit) = holes.add(Path().apply(build))

        when (character) {
            'K' -> {
                solid { bar(x, 0f, x + t, cap) }
                solid { slab(x + t * 0.9f, 0.54f * cap, x + w, -0.02f * cap, t * 0.5f) }
                solid { slab(x + t * 0.9f, 0.46f * cap, x + w, 1.02f * cap, t * 0.5f) }
            }

            'R' -> {
                // The bowl has to be deeper than two strokes or its counter comes out negative and
                // reads as a slot rather than as a hole.
                val right = x + w - t * 0.1f
                val bottom = 0.64f * cap
                val waist = t * 0.82f
                solid { bar(x, 0f, x + t, cap) }
                solid { bar(x, 0f, right, t) }
                solid { bar(x, bottom - waist, right, bottom) }
                solid { bar(right - t, 0f, right, bottom) }
                solid { slab(x + (right - x) * 0.60f, bottom - t * 0.2f, x + w, 1.02f * cap, t * 0.5f) }
                hole { bar(x + t, t, right - t, bottom - waist) }
            }

            'A' -> {
                // A flat top wider than one stroke. Narrower and the legs overlap up there, which
                // closes the counter until halfway down the letter.
                val apex = t * 1.15f
                val cross = 0.64f * cap
                solid { slab(x + w / 2 - apex / 2, 0f, x + t * 0.5f, cap, t * 0.5f) }
                solid { slab(x + w / 2 + apex / 2, 0f, x + w - t * 0.5f, cap, t * 0.5f) }
                solid { bar(x + t * 0.62f, cross, x + w - t * 0.62f, cross + t * 0.70f) }
            }

            'T' -> {
                solid { bar(x, 0f, x + w, t) }
                solid { bar(x + w / 2 - t / 2, 0f, x + w / 2 + t / 2, cap) }
            }

            'O' -> {
                // A ring has no self-overlap, so even-odd states it directly and needs no union or
                // difference to go wrong.
                return Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(x, 0f, x + w, cap))
                    addOval(Rect(x + t, t, x + w - t, cap - t))
                }
            }

            'Z' -> {
                solid { bar(x, 0f, x + w, t) }
                solid { bar(x, cap - t, x + w, cap) }
                solid { slab(x + w - t * 0.55f, t * 0.85f, x + t * 0.55f, cap - t * 0.85f, t * 0.6f) }
            }

            else -> error("The wordmark has no glyph for '$character'")
        }

        val filled = pieces.reduce { whole, piece ->
            Path().apply { op(whole, piece, PathOperation.Union) }
        }
        return holes.fold(filled) { whole, cut ->
            Path().apply { op(whole, cut, PathOperation.Difference) }
        }
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
