package com.przunk.kratkoza.domain.settings

/**
 * The colour scheme the board and the accents are drawn in (project.md sections 9 and 35).
 *
 * project.md section 39 leaves the *set* of palettes open, so these are a starting point, not
 * a closed list. Each is meant to be calm rather than loud, as section 9 requires — the board is
 * something people stare at for twenty minutes, not a splash screen.
 *
 * Stored by [name], like every other enum here; the constant's name is part of the on-disk format.
 */
enum class BoardPalette {
    /** Cool blue-grey. The original look, and the default. */
    SLATE,

    /** Warm off-white and brown, closer to a printed puzzle book. */
    SEPIA,

    /** Muted green. */
    FOREST,

    /** Neutral grey with no hue at all, for anyone who finds tinted boards distracting. */
    GRAPHITE,

    /**
     * Cream and muted honey: soft contrast with a yellow accent (decided 2026-11-12).
     *
     * Gentler than the others on purpose — warm ink instead of near-black, block lines in olive
     * brown — but it still clears every threshold in `PalettesTest`. Yellow carries the highlights
     * and the accent, never the digits: yellow ink on a light board is unreadable.
     */
    HONEY,
    ;

    companion object {
        fun fromStorageName(value: String): BoardPalette? = entries.firstOrNull { it.name == value }
    }
}
