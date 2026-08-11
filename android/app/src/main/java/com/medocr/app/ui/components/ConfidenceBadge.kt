package com.medocr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.ConfidenceLevel
import com.medocr.app.data.model.confidenceLevel
import com.medocr.app.ui.theme.LocalExtendedColors

@Composable
fun ConfidenceBadge(value: Double, modifier: Modifier = Modifier) {
    val extended = LocalExtendedColors.current
    val (bg, fg) = when (confidenceLevel(value)) {
        ConfidenceLevel.GOOD -> extended.successContainer to extended.success
        ConfidenceLevel.MEDIUM -> extended.warningContainer to extended.warning
        ConfidenceLevel.LOW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Text(
        text = "${(value * 100).toInt()}%",
        color = fg,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
