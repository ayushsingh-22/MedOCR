package com.medocr.app.data.model

/** Outcome of running OCR on a single image. */
data class OcrResult(
    val dateGroups: List<DateGroup> = emptyList(),
    val modelUsed: String? = null,
    val error: String? = null,
)

data class AppendResult(
    val rowsAdded: Int,
    val updatedRange: String = "",
)
