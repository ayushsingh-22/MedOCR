package com.medocr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedOcrTopBar(themeMode: ThemeMode, onThemeToggle: () -> Unit, onApiKeyClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Column {
                Text("🏥 MedOCR", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        actions = {
            IconButton(
                onClick = onApiKeyClick,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text("🔑", style = MaterialTheme.typography.titleMedium)
            }
            ThemeToggleButton(themeMode = themeMode, onClick = onThemeToggle)
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
