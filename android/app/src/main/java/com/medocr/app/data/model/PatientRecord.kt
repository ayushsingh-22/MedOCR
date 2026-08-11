package com.medocr.app.data.model

import java.util.UUID

data class FieldConfidence(
    val name: Double = 0.8,
    val age: Double = 0.8,
    val gender: Double = 0.8,
    val tests: Double = 0.8,
    val amount: Double = 0.8,
)

/**
 * A single patient row inside a [DateGroup]. [localId] is UI-only (stable key for
 * Compose lists / edits) and never sent to the backend.
 */
data class PatientRecord(
    val localId: String = UUID.randomUUID().toString(),
    val serial: Int? = null,
    val name: String = "",
    val age: String = "",
    val gender: String = "M",
    val tests: String = "",
    val amount: String? = null,
    val crossedOut: Boolean = false,
    val confidence: FieldConfidence = FieldConfidence(),
    val rawText: String = "",
) {
    /** True when [amount] holds text that isn't empty and isn't a valid number. */
    val hasInvalidAmount: Boolean
        get() = !amount.isNullOrBlank() && amount.toDoubleOrNull() == null

    fun isFieldLowConfidence(value: Double): Boolean = value < 0.6
}

/** A group of patients recorded under the same date, as extracted from one or more images. */
data class DateGroup(
    val localId: String = UUID.randomUUID().toString(),
    val date: String = "",
    val dateConfidence: Double = 0.0,
    val patients: List<PatientRecord> = emptyList(),
)

enum class ConfidenceLevel { GOOD, MEDIUM, LOW }

fun confidenceLevel(value: Double): ConfidenceLevel = when {
    value >= 0.85 -> ConfidenceLevel.GOOD
    value >= 0.65 -> ConfidenceLevel.MEDIUM
    else -> ConfidenceLevel.LOW
}
