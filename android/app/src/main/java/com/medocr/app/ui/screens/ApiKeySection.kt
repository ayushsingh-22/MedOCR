package com.medocr.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.Provider
import com.medocr.app.ui.components.ApiKeyField
import com.medocr.app.ui.components.StepCard
import com.medocr.app.ui.main.KeyTestState
import com.medocr.app.ui.theme.LocalExtendedColors

/** Its own step so entering + validating a key isn't buried inside the general Settings card. */
@Composable
fun ApiKeySection(
    provider: Provider,
    apiKey: String,
    testState: KeyTestState,
    onApiKeyChanged: (String) -> Unit,
    onTestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    StepCard(step = 2, title = "API Key", subtitle = "For the ${provider.displayName} provider selected above", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ApiKeyField(
                provider = provider,
                value = apiKey,
                onValueChange = onApiKeyChanged,
                onGetKeyClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.getKeyUrl))) },
            )
            if (provider == Provider.LLAMAPARSE) {
                Text(
                    "LlamaParse uses premium vision parsing. Jobs may take up to 2 minutes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (provider == Provider.GROQ) {
                Text(
                    "Groq automatically cycles through all available vision models as fallback.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onTestClick, enabled = testState !is KeyTestState.Testing) {
                    if (testState is KeyTestState.Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Testing...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Test Key", fontWeight = FontWeight.SemiBold)
                    }
                }
                KeyTestStatus(testState)
            }
        }
    }
}

@Composable
private fun KeyTestStatus(testState: KeyTestState) {
    when (testState) {
        is KeyTestState.Success -> Text(
            "✅ Works",
            color = LocalExtendedColors.current.success,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        is KeyTestState.Failure -> Text(
            "❌ ${testState.message}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
        )
        else -> Unit
    }
}
