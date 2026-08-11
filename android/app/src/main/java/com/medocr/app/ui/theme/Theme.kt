package com.medocr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.medocr.app.data.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    primaryContainer = Indigo500.copy(alpha = 0.12f),
    onPrimaryContainer = Indigo600,
    secondary = Violet500,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = DangerLight,
    onError = Color.White,
    errorContainer = DangerLight.copy(alpha = 0.08f),
    onErrorContainer = DangerLight,
)

private val DarkScheme = darkColorScheme(
    primary = Indigo400,
    onPrimary = DarkBackground,
    primaryContainer = Indigo500.copy(alpha = 0.18f),
    onPrimaryContainer = Indigo400,
    secondary = Violet400,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DangerDark,
    onError = DarkBackground,
    errorContainer = DangerDark.copy(alpha = 0.12f),
    onErrorContainer = DangerDark,
)

@Composable
fun MedOcrTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MedOcrTypography,
            shapes = MedOcrShapes,
            content = content,
        )
    }
}
