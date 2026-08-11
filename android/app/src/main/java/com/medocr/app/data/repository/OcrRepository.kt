package com.medocr.app.data.repository

import android.net.Uri
import com.medocr.app.data.model.OcrResult
import com.medocr.app.data.model.Provider

interface OcrRepository {
    /**
     * Runs OCR on a single image with the given [provider].
     * @param geminiFallbackApiKey used only by the LlamaParse path to re-format
     * text into structured JSON if LlamaParse's own output isn't valid JSON;
     * pass the user's saved Gemini key if they have one, otherwise null.
     */
    suspend fun analyze(
        imageUri: Uri,
        provider: Provider,
        apiKey: String,
        geminiFallbackApiKey: String? = null,
    ): OcrResult
}
