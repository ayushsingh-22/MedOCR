package com.medocr.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LlamaParseUploadResponse(
    val id: String? = null,
    @SerialName("job_id") val jobId: String? = null,
)

@Serializable
data class LlamaParseJobStatusResponse(
    val status: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class LlamaParseResultResponse(
    val markdown: String? = null,
    val pages: List<LlamaParsePage>? = null,
)

@Serializable
data class LlamaParsePage(
    val md: String? = null,
    val text: String? = null,
)
