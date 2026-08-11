package com.medocr.app.data.remote

import com.medocr.app.data.remote.dto.GroqRequest
import com.medocr.app.data.remote.dto.GroqResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqApi {
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") bearerToken: String,
        @Body request: GroqRequest,
    ): GroqResponse

    companion object {
        const val BASE_URL = "https://api.groq.com/"
    }
}
