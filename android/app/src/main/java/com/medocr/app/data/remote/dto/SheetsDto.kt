package com.medocr.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SheetsValuesBody(val values: List<List<JsonElement>>)

@Serializable
data class SheetsValuesResponse(val values: List<List<String>>? = null)

@Serializable
data class SheetsAppendResponse(val updates: SheetsUpdates? = null)

@Serializable
data class SheetsUpdates(
    val updatedRange: String? = null,
    val updatedRows: Int? = null,
)

@Serializable
data class SheetsErrorResponse(val error: SheetsErrorBody? = null)

@Serializable
data class SheetsErrorBody(
    val message: String? = null,
    val status: String? = null,
)
