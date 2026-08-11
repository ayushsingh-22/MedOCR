package com.medocr.app.data.repository

import com.medocr.app.data.model.AppendResult
import com.medocr.app.data.model.DateGroup

interface SheetsRepository {
    /**
     * Appends date-grouped patient rows to [spreadsheetId]/[sheetName], writing a
     * header row on first use. Crossed-out (skipped) patients must already be
     * filtered out of [dateGroups] by the caller.
     */
    suspend fun appendPatientRows(
        accessToken: String,
        spreadsheetId: String,
        sheetName: String,
        dateGroups: List<DateGroup>,
    ): Result<AppendResult>
}
