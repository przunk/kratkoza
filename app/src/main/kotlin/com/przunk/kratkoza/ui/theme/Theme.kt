package com.przunk.kratkoza.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.przunk.kratkoza.domain.settings.BoardPalette
import com.przunk.kratkoza.domain.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = SlateBlue40,
    onPrimary = Neutral100,
    primaryContainer = SlateBlue90,
    onPrimaryContainer = SlateBlue10,
    secondary = Teal40,
    onSecondary = Neutral100,
    secondaryContainer = Teal80,
    onSecondaryContainer = Neutral10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = Neutral94,
    onSurfaceVariant = Neutral30,
    surfaceContainer = Neutral96,
    surfaceContainerHigh = Neutral94,
    outline = Neutral50,
    outlineVariant = Neutral80,
    error = Error40,
    onError = Neutral100,
    errorContainer = Error90,
    onErrorContainer = Error10,
)

private val DarkColors = darkColorScheme(
    primary = SlateBlue80,
    onPrimary = SlateBlue10,
    primaryContainer = SlateBlue30,
    onPrimaryContainer = SlateBlue95,
    secondary = Teal80,
    onSecondary = Neutral10,
    secondaryContainer = Teal40,
    onSecondaryContainer = Neutral100,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral80,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    outline = Neutral60,
    outlineVariant = Neutral30,
    error = Error80,
    onError = Error20,
    errorContainer = Error20,
    onErrorContainer = Error90,
)

/**
 * Whether [themeMode] resolves to a dark scheme right now.
 *
 * Exposed because the settings screen has to answer the same question to draw its palette previews,
 * and two copies of this `when` would be two places to forget.
 */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun KratkozaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: BoardPalette = BoardPalette.SLATE,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = isDarkTheme(themeMode)

    val boardColors = Palettes.boardColors(palette, darkTheme, highContrast)
    val accent = Palettes.accent(palette, darkTheme)

    CompositionLocalProvider(LocalBoardColors provides boardColors) {
        MaterialTheme(
            colorScheme = colorScheme(darkTheme, accent, highContrast),
            typography = KratkozaTypography,
            content = content,
        )
    }
}

/**
 * Material's scheme, tinted by the chosen palette.
 *
 * Only the primary roles follow the palette. Letting every role swing with it would leave surfaces
 * and outlines chasing the accent, which reads as four different apps rather than one app in four
 * moods — and project.md section 9 asks the palettes to be calm.
 */
@Composable
private fun colorScheme(dark: Boolean, accent: Color, highContrast: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val tinted = base.copy(
        primary = accent,
        onPrimary = if (dark) base.onPrimary else Neutral100,
        primaryContainer = lerp(base.surface, accent, PRIMARY_CONTAINER_MIX),
        onPrimaryContainer = if (dark) Neutral90 else Neutral10,
    )

    if (!highContrast) return tinted

    // The board is handled by Palettes; this is the rest of the screen catching up with it, so the
    // two do not end up looking like they belong to different settings.
    val ink = if (dark) Color.White else Color.Black
    val ground = if (dark) Color.Black else Color.White
    return tinted.copy(
        background = lerp(base.background, ground, SURFACE_PUSH),
        surface = lerp(base.surface, ground, SURFACE_PUSH),
        surfaceContainer = lerp(base.surfaceContainer, ground, SURFACE_PUSH),
        surfaceContainerHigh = lerp(base.surfaceContainerHigh, ground, SURFACE_PUSH),
        onBackground = lerp(base.onBackground, ink, TEXT_PUSH),
        onSurface = lerp(base.onSurface, ink, TEXT_PUSH),
        onSurfaceVariant = lerp(base.onSurfaceVariant, ink, TEXT_PUSH),
        outline = lerp(base.outline, ink, OUTLINE_PUSH),
        outlineVariant = lerp(base.outlineVariant, ink, OUTLINE_PUSH),
    )
}

private const val PRIMARY_CONTAINER_MIX = 0.22f
private const val SURFACE_PUSH = 0.55f
private const val TEXT_PUSH = 0.45f
private const val OUTLINE_PUSH = 0.35f
