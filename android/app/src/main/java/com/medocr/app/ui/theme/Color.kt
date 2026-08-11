package com.medocr.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand — shared between light & dark, matches the web app's --accent ────────
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF4F46E5)
val Indigo400 = Color(0xFF818CF8)
val Violet500 = Color(0xFF8B5CF6)
val Violet400 = Color(0xFFC084FC)
val Pink500 = Color(0xFFEC4899)
val Orange500 = Color(0xFFF97316)

// ── Light theme — mirrors static/style.css :root ────────────────────────────
val LightBackground = Color(0xFFF4F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBEBF3)
val LightOnBackground = Color(0xFF1A1A2E)
val LightOnSurfaceVariant = Color(0xFF555577)
val LightOutline = Color(0x1A000000)
val LightMuted = Color(0xFF8888AA)

// ── Dark theme — mirrors [data-theme="dark"] ────────────────────────────────
val DarkBackground = Color(0xFF080814)
val DarkSurface = Color(0xFF13131F)
val DarkSurfaceVariant = Color(0xFF1C1C2C)
val DarkOnBackground = Color(0xFFF0F0FF)
val DarkOnSurfaceVariant = Color(0xFF8888AA)
val DarkOutline = Color(0x17FFFFFF)
val DarkMuted = Color(0xFF555566)

// ── Semantic colors (no direct Material3 equivalent) ────────────────────────
val SuccessLight = Color(0xFF16A34A)
val SuccessDark = Color(0xFF4ADE80)
val WarningLight = Color(0xFFD97706)
val WarningDark = Color(0xFFFBBF24)
val DangerLight = Color(0xFFDC2626)
val DangerDark = Color(0xFFF87171)
