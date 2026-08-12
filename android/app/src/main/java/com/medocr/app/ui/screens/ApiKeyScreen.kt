package com.medocr.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.Provider
import com.medocr.app.ui.main.KeyTestState

/** Standalone page for the API key, reached from the key icon on [com.medocr.app.ui.components.MedOcrTopBar] rather than shown inline on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    provider: Provider,
    apiKey: String,
    testState: KeyTestState,
    onApiKeyChanged: (String) -> Unit,
    onTestClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Key", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 32.dp,
            ),
        ) {
            item {
                ApiKeySection(
                    provider = provider,
                    apiKey = apiKey,
                    testState = testState,
                    onApiKeyChanged = onApiKeyChanged,
                    onTestClick = onTestClick,
                )
            }
        }
    }
}
