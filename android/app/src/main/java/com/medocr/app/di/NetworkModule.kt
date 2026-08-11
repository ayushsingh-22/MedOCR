package com.medocr.app.di

import com.medocr.app.data.remote.GeminiApi
import com.medocr.app.data.remote.GroqApi
import com.medocr.app.data.remote.LlamaParseApi
import com.medocr.app.data.remote.SheetsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Body-level logging would print raw API keys and OCR text; keep it to headers only.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofitFor(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideGeminiApi(client: OkHttpClient, json: Json): GeminiApi =
        retrofitFor(GeminiApi.BASE_URL, client, json).create(GeminiApi::class.java)

    @Provides
    @Singleton
    fun provideGroqApi(client: OkHttpClient, json: Json): GroqApi =
        retrofitFor(GroqApi.BASE_URL, client, json).create(GroqApi::class.java)

    @Provides
    @Singleton
    fun provideLlamaParseApi(client: OkHttpClient, json: Json): LlamaParseApi =
        retrofitFor(LlamaParseApi.BASE_URL, client, json).create(LlamaParseApi::class.java)

    @Provides
    @Singleton
    fun provideSheetsApi(client: OkHttpClient, json: Json): SheetsApi =
        retrofitFor(SheetsApi.BASE_URL, client, json).create(SheetsApi::class.java)
}
