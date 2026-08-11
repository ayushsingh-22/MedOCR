package com.medocr.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors Material3's default ColorScheme doesn't model (success/warning). */
data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val cardBorder: Color,
    val accentGlow: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = SuccessLight,
        successContainer = SuccessLight.copy(alpha = 0.12f),
        warning = WarningLight,
        warningContainer = WarningLight.copy(alpha = 0.12f),
        cardBorder = LightOutline,
        accentGlow = Indigo500.copy(alpha = 0.18f),
    )
}

val LightExtendedColors = ExtendedColors(
    success = SuccessLight,
    successContainer = SuccessLight.copy(alpha = 0.10f),
    warning = WarningLight,
    warningContainer = WarningLight.copy(alpha = 0.10f),
    cardBorder = LightOutline,
    accentGlow = Indigo500.copy(alpha = 0.18f),
)

val DarkExtendedColors = ExtendedColors(
    success = SuccessDark,
    successContainer = SuccessDark.copy(alpha = 0.12f),
    warning = WarningDark,
    warningContainer = WarningDark.copy(alpha = 0.12f),
    cardBorder = DarkOutline,
    accentGlow = Indigo500.copy(alpha = 0.25f),
)
