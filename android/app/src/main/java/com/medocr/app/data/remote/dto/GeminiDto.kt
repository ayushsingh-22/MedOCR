package com.medocr.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
data class GeminiGenerationConfig(
    @SerialName("response_mime_type") val responseMimeType: String = "application/json",
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
