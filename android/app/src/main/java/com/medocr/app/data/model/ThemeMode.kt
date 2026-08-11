package com.medocr.app.data.model

enum class ThemeMode(val id: String, val emoji: String, val label: String) {
    LIGHT("light", "☀️", "Light"),
    DARK("dark", "🌙", "Dark"),
    SYSTEM("system", "💻", "System default"),
    ;

    fun next(): ThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class AuthState { UNKNOWN, CONNECTED, DISCONNECTED }
