package com.medocr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medocr.app.ui.theme.LocalExtendedColors

private val GENDER_OPTIONS = listOf("M", "F", "H")

@Composable
fun GenderSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Text("GENDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GENDER_OPTIONS.forEach { option ->
                val isSelected = option == selected
                val bg = if (isSelected) LocalExtendedColors.current.accentGlow else MaterialTheme.colorScheme.surfaceVariant
                val fg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

                var cellModifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                if (enabled) {
                    cellModifier = cellModifier.clickable { onSelect(option) }
                }

                Text(
                    text = option,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = cellModifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
