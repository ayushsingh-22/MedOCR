package com.medocr.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.medocr.app.ui.theme.LocalExtendedColors

/** OutlinedTextField that tints itself amber on low OCR confidence, red when [isInvalid]. */
@Composable
fun ConfidenceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    confidence: Double,
    modifier: Modifier = Modifier,
    isInvalid: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val extended = LocalExtendedColors.current
    val isLow = confidence < 0.6
    val containerColor = when {
        isInvalid -> MaterialTheme.colorScheme.errorContainer
        isLow -> extended.warningContainer
        else -> Color.Transparent
    }
    val borderColor = when {
        isInvalid -> MaterialTheme.colorScheme.error
        isLow -> extended.warning
        else -> MaterialTheme.colorScheme.outline
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = containerColor,
            focusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            unfocusedBorderColor = borderColor,
            disabledBorderColor = borderColor.copy(alpha = 0.4f),
        ),
    )
}
