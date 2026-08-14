package com.medocr.app.data.repository

import com.medocr.app.data.model.AppendResult
import com.medocr.app.data.model.DateGroup
import com.medocr.app.data.remote.SheetsApi
import com.medocr.app.data.remote.dto.SheetsValuesBody
import com.medocr.app.util.DateUtils
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SheetsRepositoryImpl @Inject constructor(
    private val sheetsApi: SheetsApi,
) : SheetsRepository {

    private val headerRow = listOf("Date", "Name", "Test", "Amount")

    override suspend fun appendPatientRows(
        accessToken: String,
        spreadsheetId: String,
        sheetName: String,
        dateGroups: List<DateGroup>,
    ): Result<AppendResult> = try {
        val bearer = "Bearer $accessToken"
        // Trim to guard against stray whitespace from copy-pasted IDs/names, which would
        // otherwise be sent as a literal %20 and make Google return a 404 "not found".
        val trimmedSpreadsheetId = spreadsheetId.trim()
        val trimmedSheetName = sheetName.trim()
        ensureHeaderRow(bearer, trimmedSpreadsheetId, trimmedSheetName)

        val rows = buildRows(dateGroups)
        if (rows.isEmpty()) {
            Result.failure(IllegalStateException("No patient rows to append."))
        } else {
            val response = sheetsApi.appendValues(
                bearerToken = bearer,
                spreadsheetId = trimmedSpreadsheetId,
                range = "$trimmedSheetName!A:D",
                body = SheetsValuesBody(rows),
            )
            Result.success(
                AppendResult(
                    rowsAdded = response.updates?.updatedRows ?: (rows.size),
                    updatedRange = response.updates?.updatedRange ?: "",
                ),
            )
        }
    } catch (e: HttpException) {
        if (e.code() == 401) {
            Result.failure(ExpiredSheetsTokenException())
        } else {
            Result.failure(IllegalStateException("Google Sheets API error: ${e.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(IllegalStateException("Unexpected error: ${e.message}"))
    }

    private suspend fun ensureHeaderRow(bearer: String, spreadsheetId: String, sheetName: String) {
        try {
            val range = "$sheetName!A1:D1"
            val existing = sheetsApi.getValues(bearer, spreadsheetId, range).values?.firstOrNull()
            if (existing == null) {
                sheetsApi.updateValues(
                    bearerToken = bearer,
                    spreadsheetId = spreadsheetId,
                    range = range,
                    valueInputOption = "RAW",
                    body = SheetsValuesBody(listOf(headerRow.map { JsonPrimitive(it) })),
                )
            }
        } catch (e: Exception) {
            // Best-effort — if the sheet already has content the header write is skipped upstream anyway.
        }
    }

    /** Mirrors `sheets_writer.py::append_patient_rows`'s row-building rules. */
    private fun buildRows(dateGroups: List<DateGroup>): List<List<JsonElement>> {
        val sortedGroups = dateGroups
            .filter { it.patients.isNotEmpty() }
            .sortedBy { DateUtils.parseDateForSort(it.date) }

        val rows = mutableListOf<List<JsonElement>>()
        for (group in sortedGroups) {
            val formattedDate = DateUtils.formatDateForSheet(group.date)
            rows.add(listOf(JsonPrimitive(""), JsonPrimitive(""), JsonPrimitive(""), JsonPrimitive("")))

            group.patients.forEachIndexed { index, patient ->
                val nameCol = DateUtils.formatNameAgeGender(patient.name, patient.age, patient.gender)
                val testsCol = patient.tests.trim().uppercase()
                val amountCol: JsonElement = patient.amount?.toDoubleOrNull()?.toInt()
                    ?.let { JsonPrimitive(it) } ?: JsonPrimitive("")
                // Leading apostrophe forces Sheets to store the date as plain text (matches manual entry semantics).
                val dateCol = if (index == 0 && formattedDate.isNotEmpty()) "'$formattedDate" else ""

                rows.add(listOf(JsonPrimitive(dateCol), JsonPrimitive(nameCol), JsonPrimitive(testsCol), amountCol))
            }
        }
        return rows
    }
}
