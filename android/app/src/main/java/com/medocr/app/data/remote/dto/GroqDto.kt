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

/**
 * Vision-capable models tried in order — next one is used if the current one fails.
 * The Llama 3.2/4 vision preview models this list originally shipped with have since
 * been decommissioned by Groq; `qwen/qwen3.6-27b` is the current (as of mid-2026)
 * vision-capable chat model. Kept as a list so a second option can be added back
 * without another code change if Groq reintroduces one.
 */
val GROQ_VISION_MODELS = listOf(
    "qwen/qwen3.6-27b",
)
