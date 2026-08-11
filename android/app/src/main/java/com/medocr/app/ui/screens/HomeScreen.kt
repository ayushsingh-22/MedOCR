package com.medocr.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medocr.app.ui.components.MedOcrTopBar
import com.medocr.app.ui.main.KeyTestState
import com.medocr.app.ui.main.MainViewModel
import com.medocr.app.ui.main.ToastType
import com.medocr.app.ui.main.UiEvent

@Composable
fun HomeScreen(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onAuthorizationResult(result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Toast -> snackbarHostState.showSnackbar(
                    message = (if (event.type == ToastType.SUCCESS) "✅ " else if (event.type == ToastType.ERROR) "❌ " else "") + event.message,
                    withDismissAction = event.type == ToastType.ERROR,
                    duration = if (event.type == ToastType.ERROR) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                is UiEvent.RequestAuthorization -> authLauncher.launch(event.intentSenderRequest)
            }
        }
    }

    Scaffold(
        topBar = { MedOcrTopBar(themeMode = state.themeMode, onThemeToggle = viewModel::onThemeToggle) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 32.dp,
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            item(key = "settings") {
                SettingsSection(
                    provider = state.provider,
                    sheetId = state.sheetId,
                    sheetName = state.sheetName,
                    authState = state.authState,
                    authError = state.authError,
                    onProviderSelected = viewModel::onProviderSelected,
                    onSheetIdChanged = viewModel::onSheetIdChanged,
                    onSheetNameChanged = viewModel::onSheetNameChanged,
                    onConnectClick = viewModel::onConnectAccountClicked,
                    onDisconnectClick = viewModel::onDisconnectClicked,
                )
            }

            item(key = "apiKey") {
                ApiKeySection(
                    provider = state.provider,
                    apiKey = state.currentApiKey,
                    testState = state.keyTestState[state.provider] ?: KeyTestState.Idle,
                    onApiKeyChanged = { viewModel.onApiKeyChanged(state.provider, it) },
                    onTestClick = { viewModel.onTestApiKeyClicked(state.provider) },
                )
            }

            item(key = "upload") {
                UploadSection(
                    selectedImages = state.selectedImages,
                    isAnalyzing = state.isAnalyzing,
                    batchProgress = state.batchProgress,
                    provider = state.provider,
                    onImagesPicked = viewModel::onImagesSelected,
                    onRemoveImage = viewModel::onRemoveImage,
                    onClearAll = viewModel::onClearImages,
                    onAnalyzeClick = viewModel::onAnalyzeClicked,
                )
            }

            if (state.hasReviewData) {
                item(key = "review") {
                    ReviewSection(
                        dateGroups = state.dateGroups,
                        isAppending = state.isAppending,
                        onDateChanged = viewModel::onDateChanged,
                        onNameChanged = { g, p, v -> viewModel.onPatientChanged(g, p) { it.copy(name = v) } },
                        onAgeChanged = { g, p, v -> viewModel.onPatientChanged(g, p) { it.copy(age = v) } },
                        onGenderChanged = { g, p, v -> viewModel.onPatientChanged(g, p) { it.copy(gender = v) } },
                        onTestsChanged = { g, p, v -> viewModel.onPatientChanged(g, p) { it.copy(tests = v) } },
                        onAmountChanged = { g, p, v -> viewModel.onPatientChanged(g, p) { it.copy(amount = v.ifBlank { null }) } },
                        onToggleCrossedOut = viewModel::onToggleCrossedOut,
                        onDeleteRow = viewModel::onDeleteRow,
                        onAddRow = viewModel::onAddRow,
                        onRemoveGroup = viewModel::onRemoveGroup,
                        onAppendClick = viewModel::onAppendClicked,
                    )
                }
            }
        }
    }
}
