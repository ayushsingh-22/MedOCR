package com.medocr.app.data.remote

import com.medocr.app.data.remote.dto.GeminiRequest
import com.medocr.app.data.remote.dto.GeminiResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest,
    ): GeminiResponse

    /** Cheap, no-quota-cost call used only to validate that an API key works. */
    @GET("v1beta/models")
    suspend fun listModels(@Query("key") apiKey: String): Response<JsonElement>

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
        const val MODEL = "gemini-2.0-flash"
    }
}
