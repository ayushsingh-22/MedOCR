package com.medocr.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.medocr.app.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedOcrTopBar(themeMode: ThemeMode, onThemeToggle: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Column {
                Text("🏥 MedOCR", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        actions = { ThemeToggleButton(themeMode = themeMode, onClick = onThemeToggle) },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
