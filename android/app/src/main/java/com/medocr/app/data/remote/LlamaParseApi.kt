package com.medocr.app.data.remote

import com.medocr.app.data.remote.dto.LlamaParseJobStatusResponse
import com.medocr.app.data.remote.dto.LlamaParseResultResponse
import com.medocr.app.data.remote.dto.LlamaParseUploadResponse
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface LlamaParseApi {
    /**
     * There's no dedicated "whoami"/models endpoint, so validate the key by
     * probing a job id that can't exist: a 401/403 means the key is rejected,
     * anything else (e.g. 404 "job not found") means it was accepted.
     */
    @GET("api/parsing/job/{jobId}")
    suspend fun checkAuth(
        @Header("Authorization") bearerToken: String,
        @Path("jobId") jobId: String,
    ): Response<JsonElement>

    @Multipart
    @POST("api/parsing/upload")
    suspend fun upload(
        @Header("Authorization") bearerToken: String,
        @Part file: MultipartBody.Part,
        @Part("parsing_instruction") instruction: RequestBody,
        @Part("result_type") resultType: RequestBody,
        @Part("language") language: RequestBody,
    ): LlamaParseUploadResponse

    @GET("api/parsing/job/{jobId}")
    suspend fun getJobStatus(
        @Header("Authorization") bearerToken: String,
        @Path("jobId") jobId: String,
    ): LlamaParseJobStatusResponse

    @GET("api/parsing/job/{jobId}/result/markdown")
    suspend fun getResult(
        @Header("Authorization") bearerToken: String,
        @Path("jobId") jobId: String,
    ): LlamaParseResultResponse

    companion object {
        const val BASE_URL = "https://api.cloud.llamaindex.ai/"
    }
}
