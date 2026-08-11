package com.medocr.app.ui.main

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medocr.app.data.local.SecureKeyStore
import com.medocr.app.data.local.SettingsDataStore
import com.medocr.app.data.model.AuthState
import com.medocr.app.data.model.DateGroup
import com.medocr.app.data.model.PatientRecord
import com.medocr.app.data.model.Provider
import com.medocr.app.data.repository.AuthOutcome
import com.medocr.app.data.repository.AuthRepository
import com.medocr.app.data.repository.OcrRepository
import com.medocr.app.data.repository.SheetsRepository
import com.medocr.app.util.DateUtils
import com.medocr.app.util.TestNameMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val secureKeyStore: SecureKeyStore,
    private val authRepository: AuthRepository,
    private val ocrRepository: OcrRepository,
    private val sheetsRepository: SheetsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsDataStore.themeMode,
                settingsDataStore.provider,
                settingsDataStore.sheetId,
                settingsDataStore.sheetName,
            ) { theme, provider, sheetId, sheetName ->
                Quad(theme, provider, sheetId, sheetName)
            }.collect { (theme, provider, sheetId, sheetName) ->
                _state.update {
                    it.copy(themeMode = theme, provider = provider, sheetId = sheetId, sheetName = sheetName)
                }
            }
        }
        viewModelScope.launch {
            secureKeyStore.keys.collect { keys -> _state.update { it.copy(apiKeys = keys) } }
        }
        viewModelScope.launch {
            authRepository.authState.collect { authState -> _state.update { it.copy(authState = authState) } }
        }
        // Google's Authorization API persists prior grants — try a silent re-authorize on cold start
        // so a returning user shows "Connected" without tapping the button again.
        viewModelScope.launch {
            val outcome = authRepository.tryAuthorize()
            if (outcome is AuthOutcome.Failed) {
                _state.update { it.copy(authState = AuthState.DISCONNECTED) }
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    // ── Theme & Settings ─────────────────────────────────────────────────────
    fun onThemeToggle() {
        val next = _state.value.themeMode.next()
        viewModelScope.launch { settingsDataStore.setThemeMode(next) }
        emitToast("Theme: ${next.label}")
    }

    fun onProviderSelected(provider: Provider) {
        viewModelScope.launch { settingsDataStore.setProvider(provider) }
    }

    fun onApiKeyChanged(provider: Provider, value: String) {
        secureKeyStore.setKey(provider, value)
        // A changed key invalidates whatever the last test result said.
        _state.update { it.copy(keyTestState = it.keyTestState + (provider to KeyTestState.Idle)) }
    }

    fun onTestApiKeyClicked(provider: Provider) {
        val apiKey = _state.value.apiKeys[provider].orEmpty()
        if (apiKey.isBlank()) {
            emitToast("⚠️ Enter a ${provider.displayName} API key first", ToastType.ERROR)
            return
        }
        _state.update { it.copy(keyTestState = it.keyTestState + (provider to KeyTestState.Testing)) }
        viewModelScope.launch {
            val result = ocrRepository.testApiKey(provider, apiKey)
            val newState = result.fold(
                onSuccess = { KeyTestState.Success },
                onFailure = { KeyTestState.Failure(it.message ?: "Test failed.") },
            )
            _state.update { it.copy(keyTestState = it.keyTestState + (provider to newState)) }
            if (newState is KeyTestState.Success) {
                emitToast("✅ ${provider.displayName} API key works!", ToastType.SUCCESS)
            } else if (newState is KeyTestState.Failure) {
                emitToast("❌ ${newState.message}", ToastType.ERROR)
            }
        }
    }

    fun onSheetIdChanged(value: String) {
        _state.update { it.copy(sheetId = value) }
        viewModelScope.launch { settingsDataStore.setSheetId(value) }
    }

    fun onSheetNameChanged(value: String) {
        _state.update { it.copy(sheetName = value) }
        viewModelScope.launch { settingsDataStore.setSheetName(value) }
    }

    // ── Google auth ──────────────────────────────────────────────────────────
    fun onConnectAccountClicked() {
        _state.update { it.copy(authError = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.tryAuthorize()) {
                is AuthOutcome.Authorized -> emitToast("✅ Google Account connected!", ToastType.SUCCESS)
                is AuthOutcome.ResolutionRequired -> eventChannel.send(UiEvent.RequestAuthorization(outcome.intentSenderRequest))
                is AuthOutcome.Failed -> failAuth(outcome.message)
            }
        }
    }

    fun onAuthorizationResult(data: Intent?) {
        when (val outcome = authRepository.handleAuthorizationResult(data)) {
            is AuthOutcome.Authorized -> {
                _state.update { it.copy(authError = null) }
                emitToast("✅ Google Account connected!", ToastType.SUCCESS)
            }
            is AuthOutcome.ResolutionRequired -> viewModelScope.launch {
                eventChannel.send(UiEvent.RequestAuthorization(outcome.intentSenderRequest))
            }
            is AuthOutcome.Failed -> failAuth(outcome.message)
        }
    }

    fun onDisconnectClicked() {
        authRepository.signOut()
        _state.update { it.copy(authError = null) }
        emitToast("Disconnected from Google")
    }

    /** Surfaces an auth failure both as a transient toast and a persistent inline message. */
    private fun failAuth(message: String) {
        _state.update { it.copy(authError = message) }
        emitToast(message, ToastType.ERROR)
    }

    // ── Image selection ──────────────────────────────────────────────────────
    fun onImagesSelected(images: List<SelectedImage>) {
        if (images.isEmpty()) return
        _state.update { it.copy(selectedImages = it.selectedImages + images) }
    }

    fun onRemoveImage(index: Int) {
        _state.update { it.copy(selectedImages = it.selectedImages.toMutableList().apply { removeAt(index) }) }
    }

    fun onClearImages() {
        _state.update { it.copy(selectedImages = emptyList(), dateGroups = emptyList()) }
    }

    // ── Analyse (batch multi-image) ──────────────────────────────────────────
    fun onAnalyzeClicked() {
        val current = _state.value
        if (current.selectedImages.isEmpty()) {
            emitToast("⚠️ No images selected", ToastType.ERROR)
            return
        }
        if (current.currentApiKey.isBlank()) {
            emitToast("⚠️ ${current.provider.displayName} API key is required. Enter it in Settings.", ToastType.ERROR)
            return
        }

        viewModelScope.launch {
            val images = current.selectedImages
            val provider = current.provider
            val apiKey = current.currentApiKey
            val geminiFallback = current.apiKeys[Provider.GEMINI]

            val allGroups = mutableListOf<DateGroup>()
            var successCount = 0
            var errorCount = 0

            _state.update { it.copy(isAnalyzing = true, batchProgress = BatchProgress(0, images.size, "Starting analysis...")) }

            images.forEachIndexed { index, image ->
                _state.update {
                    it.copy(batchProgress = BatchProgress(index, images.size, "Analysing image ${index + 1} of ${images.size}: ${image.displayName}"))
                }
                val result = ocrRepository.analyze(image.uri, provider, apiKey, geminiFallback)
                if (result.error != null) {
                    errorCount++
                    emitToast("⚠️ Image ${index + 1} failed: ${result.error}", ToastType.ERROR)
                } else {
                    allGroups += result.dateGroups
                    successCount++
                }
            }

            val normalized = allGroups.map { group ->
                group.copy(patients = group.patients.map { p -> p.copy(tests = TestNameMatcher.normalizeTests(p.tests)) })
            }
            val merged = DateUtils.mergeAndSortDateGroups(normalized)
            val totalPatients = merged.sumOf { it.patients.size }

            _state.update { it.copy(isAnalyzing = false, batchProgress = null) }

            if (totalPatients == 0) {
                emitToast("❌ No patient data extracted from any image", ToastType.ERROR)
                return@launch
            }

            _state.update { it.copy(dateGroups = merged) }
            val summary = "✅ Extracted $totalPatients patient${if (totalPatients != 1) "s" else ""} from $successCount image${if (successCount != 1) "s" else ""}"
            emitToast(if (errorCount > 0) "$summary ($errorCount failed)" else summary, ToastType.SUCCESS)
        }
    }

    // ── Review & edit ────────────────────────────────────────────────────────
    fun onDateChanged(groupId: String, newDate: String) {
        updateGroup(groupId) { it.copy(date = newDate) }
    }

    fun onPatientChanged(groupId: String, patientId: String, transform: (PatientRecord) -> PatientRecord) {
        updateGroup(groupId) { group ->
            group.copy(patients = group.patients.map { if (it.localId == patientId) transform(it) else it })
        }
    }

    fun onToggleCrossedOut(groupId: String, patientId: String, checked: Boolean) {
        onPatientChanged(groupId, patientId) { it.copy(crossedOut = checked) }
    }

    fun onDeleteRow(groupId: String, patientId: String) {
        _state.update { state ->
            val groups = state.dateGroups.mapNotNull { group ->
                if (group.localId != groupId) return@mapNotNull group
                val remaining = group.patients.filterNot { it.localId == patientId }
                if (remaining.isEmpty() && state.dateGroups.size > 1) null else group.copy(patients = remaining)
            }
            state.copy(dateGroups = groups)
        }
    }

    fun onAddRow(groupId: String) {
        updateGroup(groupId) { group ->
            group.copy(patients = group.patients + PatientRecord(serial = group.patients.size + 1, gender = "M"))
        }
    }

    fun onRemoveGroup(groupId: String) {
        _state.update { state ->
            if (state.dateGroups.size <= 1) state else state.copy(dateGroups = state.dateGroups.filterNot { it.localId == groupId })
        }
    }

    private fun updateGroup(groupId: String, transform: (DateGroup) -> DateGroup) {
        _state.update { state ->
            state.copy(dateGroups = state.dateGroups.map { if (it.localId == groupId) transform(it) else it })
        }
    }

    // ── Append to Google Sheets ──────────────────────────────────────────────
    fun onAppendClicked() {
        val current = _state.value
        if (current.sheetId.isBlank()) {
            emitToast("⚠️ Please enter the Google Sheet ID", ToastType.ERROR)
            return
        }
        if (current.dateGroups.any { it.date.isBlank() }) {
            emitToast("⚠️ One of the dates is empty — please fill it in", ToastType.ERROR)
            return
        }
        if (current.hasAnyInvalidAmount) {
            emitToast("❌ Fix invalid Amount fields (highlighted in red)", ToastType.ERROR)
            return
        }
        if (current.authState != AuthState.CONNECTED) {
            emitToast("Not authenticated. Please connect your Google Account first.", ToastType.ERROR)
            return
        }

        val payload = current.dateGroups
            .map { group -> group.copy(patients = group.patients.filterNot { it.crossedOut }) }
            .filter { it.patients.isNotEmpty() }

        if (payload.isEmpty()) {
            emitToast("⚠️ All entries are marked as skipped — nothing to append", ToastType.ERROR)
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isAppending = true) }
            val token = authRepository.getAccessToken()
            if (token == null) {
                _state.update { it.copy(isAppending = false) }
                emitToast("Not authenticated. Please connect your Google Account first.", ToastType.ERROR)
                return@launch
            }

            val result = sheetsRepository.appendPatientRows(token, current.sheetId, current.sheetName.ifBlank { "Sheet1" }, payload)
            _state.update { it.copy(isAppending = false) }

            result.fold(
                onSuccess = { appended -> emitToast("✅ ${appended.rowsAdded} rows added to Sheet!", ToastType.SUCCESS) },
                onFailure = { error -> emitToast("❌ ${error.message}", ToastType.ERROR) },
            )
        }
    }

    private fun emitToast(message: String, type: ToastType = ToastType.INFO) {
        viewModelScope.launch { eventChannel.send(UiEvent.Toast(message, type)) }
    }
}
