package com.przunk.kratkoza.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A calm, low-saturation slate/blue palette. project.md asks for an interface that is readable but
 * does not "burn the eyes", so the accents stay muted and the board surfaces stay close to neutral.
 *
 * The full set of selectable palettes is still open in project.md section 39, so exactly one palette
 * is defined here.
 */

internal val SlateBlue10 = Color(0xFF0B1620)
internal val SlateBlue20 = Color(0xFF152634)
internal val SlateBlue30 = Color(0xFF1F3A5F)
internal val SlateBlue40 = Color(0xFF2E5786)
internal val SlateBlue60 = Color(0xFF6C9CC8)
internal val SlateBlue80 = Color(0xFFA8C8E4)
internal val SlateBlue90 = Color(0xFFD3E3F1)
internal val SlateBlue95 = Color(0xFFE8F1F8)

/**
 * Board highlight tints. Neutral greys carrying a trace of the accent hue rather than saturated
 * blues: the board has to read as paper, and the tiers have to be told apart by *lightness* — which
 * survives a bad screen, bright sunlight and colour-blindness — not by hue.
 */
internal val HighlightLightPeer = Color(0xFFE8EBEE)
internal val HighlightLightSameDigit = Color(0xFFD4DEE7)
internal val HighlightLightSelected = Color(0xFFAECBE4)

internal val HighlightDarkPeer = Color(0xFF2B3034)
internal val HighlightDarkSameDigit = Color(0xFF39434B)
internal val HighlightDarkSelected = Color(0xFF3D5B77)

/** Player-entered digits. Saturated enough to carry across a board full of neutral givens. */
internal val EnteredLight = Color(0xFF15599E)
internal val EnteredDark = Color(0xFF8CC6F5)

/**
 * Digits matching the selected one (project.md section 10).
 *
 * Green rather than another blue: it has to separate from the entered digits at a glance, and hue
 * is what is left once weight is spoken for. Several cells change together, so the eye catches the
 * group even where it would not catch one cell on its own.
 */
internal val MatchedLight = Color(0xFF1E6F5A)
internal val MatchedDark = Color(0xFF7FD3BA)

internal val Teal40 = Color(0xFF2C6E63)
internal val Teal80 = Color(0xFF9CD1C6)

internal val Neutral0 = Color(0xFF000000)
internal val Neutral6 = Color(0xFF101315)
internal val Neutral10 = Color(0xFF191C1E)
internal val Neutral12 = Color(0xFF1E2224)
internal val Neutral17 = Color(0xFF282C2F)
internal val Neutral20 = Color(0xFF2E3133)
internal val Neutral22 = Color(0xFF33383B)
internal val Neutral30 = Color(0xFF44484B)
internal val Neutral50 = Color(0xFF74787B)
internal val Neutral60 = Color(0xFF8E9295)
internal val Neutral80 = Color(0xFFC4C7C9)
internal val Neutral90 = Color(0xFFE1E3E5)
internal val Neutral94 = Color(0xFFEEF0F2)
internal val Neutral96 = Color(0xFFF4F6F7)
internal val Neutral98 = Color(0xFFFAFBFC)
internal val Neutral100 = Color(0xFFFFFFFF)

/** Errors are muted red rather than signal red; they are marked by shape and text as well. */
internal val Error40 = Color(0xFFA83A32)
internal val Error80 = Color(0xFFFFB4AB)
internal val Error90 = Color(0xFFFFDAD6)
internal val Error20 = Color(0xFF5F1611)
internal val Error10 = Color(0xFF410E0B)
