package com.medocr.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.1,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: List<GroqContentPart>,
)

@Serializable
data class GroqContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: GroqImageUrl? = null,
)

@Serializable
data class GroqImageUrl(val url: String)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice> = emptyList(),
    val error: GroqError? = null,
) {
    fun extractedText(): String? = choices.firstOrNull()?.message?.content
}

@Serializable
data class GroqChoice(val message: GroqResponseMessage? = null)

@Serializable
data class GroqResponseMessage(val content: String? = null)

@Serializable
data class GroqError(val message: String? = null)

/** Vision models tried in order — next one is used if the current one fails. */
val GROQ_VISION_MODELS = listOf(
    "meta-llama/llama-4-scout-17b-16e-instruct",
    "meta-llama/llama-4-maverick-17b-128e-instruct",
    "llama-3.2-90b-vision-preview",
    "llama-3.2-11b-vision-preview",
)
