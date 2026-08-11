package com.medocr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.ui.theme.Indigo500
import com.medocr.app.ui.theme.LocalExtendedColors
import com.medocr.app.ui.theme.Violet500

/**
 * The numbered card shell used for every step (Settings / Upload / Review),
 * mirroring `.card` + `.card-header` + `.step-badge` from static/style.css.
 */
@Composable
fun StepCard(
    step: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    headerActions: @Composable (RowScope.() -> Unit)? = null,
    content: ColumnScopeContent,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalExtendedColors.current.cardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StepBadge(step)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                headerActions?.invoke(this)
            }
            Column(modifier = Modifier.padding(top = 20.dp), content = content)
        }
    }
}

private typealias ColumnScopeContent = @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit)

@Composable
private fun StepBadge(step: Int) {
    Row(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Indigo500, Violet500))),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = step.toString(),
            modifier = Modifier.align(Alignment.CenterVertically),
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
