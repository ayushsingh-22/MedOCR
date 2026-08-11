package com.medocr.app.data.model

/** OCR backend the user can pick per analysis run, mirrors the web app's provider toggle. */
enum class Provider(
    val id: String,
    val displayName: String,
    val emoji: String,
    val keyPlaceholder: String,
    val getKeyUrl: String,
) {
    GEMINI("gemini", "Gemini", "✨", "AIza...", "https://aistudio.google.com/app/apikey"),
    GROQ("groq", "Groq", "⚡", "gsk_...", "https://console.groq.com/keys"),
    LLAMAPARSE("llamaparse", "LlamaParse", "🦙", "llx-...", "https://cloud.llamaindex.ai/api-key"),
    ;

    companion object {
        fun fromId(id: String?): Provider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}
