package com.medocr.app.data.repository

import android.content.Context
import android.net.Uri
import com.medocr.app.data.model.OcrResult
import com.medocr.app.data.model.Provider
import com.medocr.app.data.remote.GeminiApi
import com.medocr.app.data.remote.GroqApi
import com.medocr.app.data.remote.LlamaParseApi
import com.medocr.app.data.remote.OcrJsonParser
import com.medocr.app.data.remote.OcrPrompt
import com.medocr.app.data.remote.dto.GROQ_VISION_MODELS
import com.medocr.app.data.remote.dto.GeminiContent
import com.medocr.app.data.remote.dto.GeminiInlineData
import com.medocr.app.data.remote.dto.GeminiPart
import com.medocr.app.data.remote.dto.GeminiRequest
import com.medocr.app.data.remote.dto.GroqContentPart
import com.medocr.app.data.remote.dto.GroqImageUrl
import com.medocr.app.data.remote.dto.GroqMessage
import com.medocr.app.data.remote.dto.GroqRequest
import com.medocr.app.util.ImageProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiApi: GeminiApi,
    private val groqApi: GroqApi,
    private val llamaParseApi: LlamaParseApi,
) : OcrRepository {

    override suspend fun analyze(
        imageUri: Uri,
        provider: Provider,
        apiKey: String,
        geminiFallbackApiKey: String?,
    ): OcrResult = try {
        when (provider) {
            Provider.GEMINI -> analyzeWithGemini(imageUri, apiKey)
            Provider.GROQ -> analyzeWithGroq(imageUri, apiKey)
            Provider.LLAMAPARSE -> analyzeWithLlamaParse(imageUri, apiKey, geminiFallbackApiKey)
        }
    } catch (e: Exception) {
        OcrResult(error = "${provider.displayName} OCR error: ${e.message}")
    }

    override suspend fun testApiKey(provider: Provider, apiKey: String): Result<Unit> = try {
        when (provider) {
            Provider.GEMINI -> {
                val response = geminiApi.listModels(apiKey)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Invalid Gemini API key (HTTP ${response.code()})."))
                }
            }
            Provider.GROQ -> {
                val response = groqApi.listModels("Bearer $apiKey")
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Invalid Groq API key (HTTP ${response.code()})."))
                }
            }
            Provider.LLAMAPARSE -> {
                val response = llamaParseApi.checkAuth("Bearer $apiKey", "medocr-key-check")
                if (response.code() == 401 || response.code() == 403) {
                    Result.failure(IllegalStateException("Invalid LlamaParse API key (HTTP ${response.code()})."))
                } else {
                    Result.success(Unit)
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(IllegalStateException("Could not reach ${provider.displayName}: ${e.message}"))
    }

    private suspend fun analyzeWithGemini(imageUri: Uri, apiKey: String): OcrResult {
        val base64 = ImageProcessor.toBase64(context, imageUri)
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = OcrPrompt.TEXT),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64)),
                    ),
                ),
            ),
        )
        val response = geminiApi.generateContent(GeminiApi.MODEL, apiKey, request)
        val text = response.extractedText()
            ?: return OcrResult(error = response.error?.message ?: "Empty response from Gemini.")
        return OcrJsonParser.parse(text)
    }

    private suspend fun analyzeWithGroq(imageUri: Uri, apiKey: String): OcrResult {
        val base64 = ImageProcessor.toBase64(context, imageUri)
        val dataUrl = "data:image/jpeg;base64,$base64"
        val errors = mutableListOf<String>()

        for (model in GROQ_VISION_MODELS) {
            try {
                val request = GroqRequest(
                    model = model,
                    messages = listOf(
                        GroqMessage(
                            role = "user",
                            content = listOf(
                                GroqContentPart(type = "text", text = OcrPrompt.TEXT),
                                GroqContentPart(type = "image_url", imageUrl = GroqImageUrl(dataUrl)),
                            ),
                        ),
                    ),
                )
                val response = groqApi.chatCompletion("Bearer $apiKey", request)
                val text = response.extractedText()
                    ?: throw IllegalStateException(response.error?.message ?: "empty response")
                val result = OcrJsonParser.parse(text)
                if (result.error == null) return result.copy(modelUsed = model)
                errors.add("[$model] ${result.error}")
            } catch (e: Exception) {
                errors.add("[$model] ${e.message}")
            }
        }
        return OcrResult(error = "All Groq models failed.\n${errors.joinToString("\n")}")
    }

    private suspend fun analyzeWithLlamaParse(
        imageUri: Uri,
        apiKey: String,
        geminiFallbackApiKey: String?,
    ): OcrResult {
        val bytes = ImageProcessor.toJpegBytes(context, imageUri)
        val bearer = "Bearer $apiKey"

        val cacheFile = File(context.cacheDir, "llamaparse_upload_${System.currentTimeMillis()}.jpg")
        cacheFile.writeBytes(bytes)
        val filePart = MultipartBody.Part.createFormData(
            "file", "image.jpg", cacheFile.asRequestBody("image/jpeg".toMediaType()),
        )

        val uploadResponse = try {
            llamaParseApi.upload(
                bearerToken = bearer,
                file = filePart,
                instruction = OcrPrompt.TEXT.toRequestBody("text/plain".toMediaType()),
                resultType = "markdown".toRequestBody("text/plain".toMediaType()),
                language = "en".toRequestBody("text/plain".toMediaType()),
            )
        } finally {
            cacheFile.delete()
        }

        val jobId = uploadResponse.id ?: uploadResponse.jobId
            ?: return OcrResult(error = "LlamaParse upload: no job ID in response.")

        // Poll for completion — jobs can take up to ~2 minutes.
        var succeeded = false
        repeat(40) {
            if (succeeded) return@repeat
            delay(3000)
            val status = llamaParseApi.getJobStatus(bearer, jobId)
            when (status.status?.uppercase()) {
                "SUCCESS" -> succeeded = true
                "ERROR", "CANCELLED", "PARTIAL_SUCCESS" -> {
                    val detail = status.error ?: status.message ?: "status=${status.status}"
                    return OcrResult(error = "LlamaParse job ended with status '${status.status}': $detail")
                }
                else -> Unit // PENDING / IN_PROGRESS — keep waiting
            }
        }
        if (!succeeded) {
            return OcrResult(error = "LlamaParse job timed out after 120 seconds (job_id=$jobId).")
        }

        val result = llamaParseApi.getResult(bearer, jobId)
        val rawText = (result.markdown ?: result.pages?.joinToString("\n") { it.md ?: it.text ?: "" } ?: "").trim()
        if (rawText.isEmpty()) {
            return OcrResult(error = "LlamaParse returned empty text — nothing was extracted from the image.")
        }

        return llamaParseToStructured(rawText, geminiFallbackApiKey)
    }

    /** Mirrors `ocr_parser.py::_llamaparse_to_structured` — direct parse, then Gemini re-format fallback. */
    private suspend fun llamaParseToStructured(rawText: String, geminiFallbackApiKey: String?): OcrResult {
        val direct = OcrJsonParser.parse(extractJsonFromText(rawText))
        if (direct.error == null && direct.dateGroups.isNotEmpty()) return direct.copy(modelUsed = "llamaparse")

        if (!geminiFallbackApiKey.isNullOrBlank()) {
            return try {
                val prompt = OcrPrompt.TEXT +
                    "\n\nThe following is the raw OCR text already extracted from the image. " +
                    "Do NOT describe an image — use only this text:\n\n$rawText"
                val request = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))))
                val response = geminiApi.generateContent(GeminiApi.MODEL, geminiFallbackApiKey, request)
                val text = response.extractedText()
                    ?: return OcrResult(error = "LlamaParse extracted text but Gemini re-format returned nothing.")
                OcrJsonParser.parse(text).copy(modelUsed = "llamaparse+gemini")
            } catch (e: Exception) {
                OcrResult(error = "LlamaParse extracted text but Gemini re-format failed: ${e.message}. Raw: ${rawText.take(400)}")
            }
        }

        return OcrResult(
            error = "LlamaParse extracted text but it isn't structured JSON. Add a Gemini key in " +
                "Settings to enable automatic re-formatting. Raw output: ${rawText.take(400)}",
        )
    }

    /** Locates the outermost `{ ... }` block in free-form text — LlamaParse may wrap JSON in prose. */
    private fun extractJsonFromText(text: String): String {
        var stripped = text.trim()
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst(Regex("^```(?:json)?\\n?"), "").replace(Regex("\\n?```$"), "").trim()
        }
        if (stripped.startsWith("{")) return stripped

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) text.substring(start, end + 1) else stripped
    }
}
