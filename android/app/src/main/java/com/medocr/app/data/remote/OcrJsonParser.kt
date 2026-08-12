package com.medocr.app.data.remote

import com.medocr.app.data.model.DateGroup
import com.medocr.app.data.model.FieldConfidence
import com.medocr.app.data.model.OcrResult
import com.medocr.app.data.model.PatientRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the free-form JSON text returned by an OCR model into our domain [OcrResult].
 * Every field is read defensively (mirrors `ocr_parser.py::_clean_patients`) because
 * vision models occasionally emit numbers as strings, omit fields, or wrap the JSON
 * in markdown fences.
 */
object OcrJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawText: String): OcrResult {
        val cleaned = extractJson(rawText)
        return try {
            val root = json.parseToJsonElement(cleaned).jsonObject
            val groupsJson = root["date_groups"]?.jsonArrayOrNull()

            val dateGroups = if (groupsJson != null) {
                groupsJson.map { it.jsonObject.toDateGroup() }
            } else {
                // Legacy single-date shape: { date, date_confidence, patients }
                listOf(root.toDateGroup())
            }
            OcrResult(dateGroups = dateGroups, error = null)
        } catch (e: Exception) {
            OcrResult(error = "Failed to parse OCR response as JSON: ${e.message}. Raw: ${cleaned.take(300)}")
        }
    }

    /**
     * Reasoning models (e.g. Groq's qwen3.6-27b) can prefix — or, if cut off mid-thought,
     * entirely replace — the JSON answer with a `<think>...</think>` block. Strip that,
     * then markdown fences, then fall back to slicing out the outermost `{ ... }` in case
     * the model still wrapped the JSON in prose.
     */
    private fun extractJson(text: String): String {
        var t = text.trim()
        t = t.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        t = stripMarkdownFences(t)
        if (t.startsWith("{")) return t

        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) t.substring(start, end + 1) else t
    }

    private fun stripMarkdownFences(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.replaceFirst(Regex("^```(?:json)?\\n?"), "")
            t = t.replace(Regex("\\n?```$"), "")
        }
        return t.trim()
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

    private fun JsonObject.toDateGroup(): DateGroup {
        val patientsJson = this["patients"]?.jsonArray ?: JsonArray(emptyList())
        return DateGroup(
            date = stringOf("date") ?: "",
            dateConfidence = doubleOf("date_confidence") ?: 0.8,
            patients = patientsJson.map { it.jsonObject.toPatient() },
        )
    }

    private fun JsonObject.toPatient(): PatientRecord {
        val confObj = this["confidence"]?.jsonObject
        val amountRaw = this["amount"]
        val amount = when {
            amountRaw == null || amountRaw is kotlinx.serialization.json.JsonNull -> null
            else -> amountRaw.jsonPrimitive.contentOrNull
                ?.let { it.replace(",", "").trim() }
                ?.toDoubleOrNull()?.toInt()?.toString()
        }
        val tests = (stringOf("tests") ?: "")
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        return PatientRecord(
            serial = intOf("serial"),
            name = (stringOf("name") ?: "").trim().uppercase(),
            age = (stringOf("age") ?: "").trim(),
            gender = (stringOf("gender") ?: "").trim().uppercase(),
            tests = tests,
            amount = amount,
            crossedOut = this["crossed_out"]?.jsonPrimitive?.booleanOrNull ?: false,
            confidence = FieldConfidence(
                name = confObj?.doubleOf("name") ?: 0.8,
                age = confObj?.doubleOf("age") ?: 0.8,
                gender = confObj?.doubleOf("gender") ?: 0.8,
                tests = confObj?.doubleOf("tests") ?: 0.8,
                amount = confObj?.doubleOf("amount") ?: 0.8,
            ),
            rawText = stringOf("raw_text") ?: "",
        )
    }

    private fun JsonObject.stringOf(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.intOf(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.doubleOf(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
}
