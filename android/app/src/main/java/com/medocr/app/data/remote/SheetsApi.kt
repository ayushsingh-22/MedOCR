package com.medocr.app.data.remote

import com.medocr.app.data.remote.dto.SheetsAppendResponse
import com.medocr.app.data.remote.dto.SheetsValuesBody
import com.medocr.app.data.remote.dto.SheetsValuesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SheetsApi {
    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range", encoded = true) range: String,
    ): SheetsValuesResponse

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range", encoded = true) range: String,
        @Query("valueInputOption") valueInputOption: String = "RAW",
        @Body body: SheetsValuesBody,
    ): SheetsAppendResponse

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Header("Authorization") bearerToken: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range", encoded = true) range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Query("insertDataOption") insertDataOption: String = "INSERT_ROWS",
        @Body body: SheetsValuesBody,
    ): SheetsAppendResponse

    companion object {
        const val BASE_URL = "https://sheets.googleapis.com/"
    }
}
