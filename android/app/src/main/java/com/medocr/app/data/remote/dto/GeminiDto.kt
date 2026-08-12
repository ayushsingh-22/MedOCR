package com.medocr.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

// Field names below are wire-format-sensitive: the Generative Language REST API
// expects lowerCamelCase JSON keys (inlineData/mimeType/responseMimeType) —
// snake_case here silently drops the image from the request.
@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val error: GeminiError? = null,
) {
    fun extractedText(): String? =
        candidates.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
}

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiError(val message: String? = null)

/**
 * Vision-capable models tried in order — next one is used if the current one fails.
 * `gemini-2.0-flash` was retired by Google on 2026-03-31 (source of the HTTP 404s);
 * these are its currently supported multimodal replacements.
 */
val GEMINI_VISION_MODELS = listOf(
    "gemini-2.5-flash",
    "gemini-2.5-pro",
    "gemini-1.5-flash",
)
