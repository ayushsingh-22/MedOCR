package com.medocr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.AuthState
import com.medocr.app.ui.theme.LocalExtendedColors

@Composable
fun AuthChip(authState: AuthState, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (authState) {
        AuthState.CONNECTED -> Triple(
            LocalExtendedColors.current.successContainer,
            LocalExtendedColors.current.success,
            "Connected",
        )
        AuthState.DISCONNECTED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "Not connected",
        )
        AuthState.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Checking...",
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(7.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
    }
}
