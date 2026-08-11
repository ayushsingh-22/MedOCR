package com.medocr.app.ui.main

import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import com.medocr.app.data.model.AuthState
import com.medocr.app.data.model.DateGroup
import com.medocr.app.data.model.Provider
import com.medocr.app.data.model.ThemeMode

data class SelectedImage(val uri: Uri, val displayName: String)

data class BatchProgress(val current: Int, val total: Int, val message: String) {
    val fraction: Float get() = if (total <= 0) 0f else (current + 0.5f) / total
}

sealed interface KeyTestState {
    data object Idle : KeyTestState
    data object Testing : KeyTestState
    data object Success : KeyTestState
    data class Failure(val message: String) : KeyTestState
}

data class MainUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val provider: Provider = Provider.GEMINI,
    val apiKeys: Map<Provider, String> = emptyMap(),
    val keyTestState: Map<Provider, KeyTestState> = emptyMap(),
    val sheetId: String = "",
    val sheetName: String = "Sheet1",
    val authState: AuthState = AuthState.UNKNOWN,
    val authError: String? = null,
    val selectedImages: List<SelectedImage> = emptyList(),
    val isAnalyzing: Boolean = false,
    val batchProgress: BatchProgress? = null,
    val dateGroups: List<DateGroup> = emptyList(),
    val isAppending: Boolean = false,
) {
    val currentApiKey: String get() = apiKeys[provider].orEmpty()
    val hasReviewData: Boolean get() = dateGroups.isNotEmpty()
    val hasAnyInvalidAmount: Boolean get() = dateGroups.any { g -> g.patients.any { it.hasInvalidAmount } }
}

sealed interface UiEvent {
    data class Toast(val message: String, val type: ToastType = ToastType.INFO) : UiEvent
    data class RequestAuthorization(val intentSenderRequest: IntentSenderRequest) : UiEvent
}

enum class ToastType { SUCCESS, ERROR, INFO }
