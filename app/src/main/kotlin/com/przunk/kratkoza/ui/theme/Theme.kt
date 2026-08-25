package com.przunk.kratkoza.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

@Composable
fun KratkozaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(
        LocalBoardColors provides if (darkTheme) DarkBoardColors else LightBoardColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = KratkozaTypography,
            content = content,
        )
    }
}
