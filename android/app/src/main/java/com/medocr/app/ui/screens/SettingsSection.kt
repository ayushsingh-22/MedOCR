package com.medocr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.AuthState
import com.medocr.app.data.model.Provider
import com.medocr.app.ui.components.AuthChip
import com.medocr.app.ui.components.ProviderToggle
import com.medocr.app.ui.components.StepCard

/** Provider choice + Google Sheet target + Google account — API keys live in their own [ApiKeySection]. */
@Composable
fun SettingsSection(
    provider: Provider,
    sheetId: String,
    sheetName: String,
    authState: AuthState,
    authError: String?,
    onProviderSelected: (Provider) -> Unit,
    onSheetIdChanged: (String) -> Unit,
    onSheetNameChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepCard(step = 1, title = "Settings", subtitle = "Choose a provider and where results are saved", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🤖 OCR PROVIDER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProviderToggle(selected = provider, onSelect = onProviderSelected)
            }

            OutlinedTextField(
                value = sheetId,
                onValueChange = onSheetIdChanged,
                label = { Text("📊 Google Sheet ID") },
                placeholder = { Text("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms") },
                supportingText = { Text("Found in the URL: docs.google.com/spreadsheets/d/[ID]/edit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )

            OutlinedTextField(
                value = sheetName,
                onValueChange = onSheetNameChanged,
                label = { Text("📋 Sheet Tab Name") },
                placeholder = { Text("Sheet1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔐 GOOGLE ACCOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AuthChip(authState = authState)
                    if (authState == AuthState.CONNECTED) {
                        OutlinedButton(onClick = onDisconnectClick) {
                            Text("Disconnect", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(onClick = onConnectClick) {
                            Text("Connect Account", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // Persistent (not just a transient snackbar) so a failure isn't missed.
                if (authError != null) {
                    Text(
                        "⚠️ $authError",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
