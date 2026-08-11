package com.medocr.app.data.remote

import com.medocr.app.data.remote.dto.GroqRequest
import com.medocr.app.data.remote.dto.GroqResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqApi {
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") bearerToken: String,
        @Body request: GroqRequest,
    ): GroqResponse

    /** Cheap call used only to validate that an API key works. */
    @GET("openai/v1/models")
    suspend fun listModels(@Header("Authorization") bearerToken: String): Response<JsonElement>

    companion object {
        const val BASE_URL = "https://api.groq.com/"
    }
}
