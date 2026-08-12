package com.medocr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medocr.app.data.model.DateGroup
import com.medocr.app.data.model.PatientRecord
import com.medocr.app.ui.components.ConfidenceBadge
import com.medocr.app.ui.components.ConfidenceTextField
import com.medocr.app.ui.components.GenderSelector
import com.medocr.app.ui.components.StepCard
import com.medocr.app.ui.theme.DangerLight
import com.medocr.app.ui.theme.LocalExtendedColors
import com.medocr.app.ui.theme.WarningLight

@Composable
fun ReviewSection(
    dateGroups: List<DateGroup>,
    isAppending: Boolean,
    onDateChanged: (String, String) -> Unit,
    onNameChanged: (String, String, String) -> Unit,
    onAgeChanged: (String, String, String) -> Unit,
    onGenderChanged: (String, String, String) -> Unit,
    onTestsChanged: (String, String, String) -> Unit,
    onAmountChanged: (String, String, String) -> Unit,
    onToggleCrossedOut: (String, String, Boolean) -> Unit,
    onDeleteRow: (String, String) -> Unit,
    onAddRow: (String) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onAppendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepCard(step = 3, title = "Review & Edit", subtitle = "Correct any OCR errors before appending", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            dateGroups.forEachIndexed { index, group ->
                DateGroupCard(
                    group = group,
                    groupIndex = index,
                    totalGroups = dateGroups.size,
                    onDateChanged = { onDateChanged(group.localId, it) },
                    onNameChanged = { pid, v -> onNameChanged(group.localId, pid, v) },
                    onAgeChanged = { pid, v -> onAgeChanged(group.localId, pid, v) },
                    onGenderChanged = { pid, v -> onGenderChanged(group.localId, pid, v) },
                    onTestsChanged = { pid, v -> onTestsChanged(group.localId, pid, v) },
                    onAmountChanged = { pid, v -> onAmountChanged(group.localId, pid, v) },
                    onToggleCrossedOut = { pid, v -> onToggleCrossedOut(group.localId, pid, v) },
                    onDeleteRow = { pid -> onDeleteRow(group.localId, pid) },
                    onAddRow = { onAddRow(group.localId) },
                    onRemoveGroup = { onRemoveGroup(group.localId) },
                )
            }

            LegendRow()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAppendClick, enabled = !isAppending, modifier = Modifier.fillMaxWidth()) {
                    if (isAppending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Text("  Appending...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("📤 Append to Google Sheet", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Only non-skipped rows will be appended",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DateGroupCard(
    group: DateGroup,
    groupIndex: Int,
    totalGroups: Int,
    onDateChanged: (String) -> Unit,
    onNameChanged: (String, String) -> Unit,
    onAgeChanged: (String, String) -> Unit,
    onGenderChanged: (String, String) -> Unit,
    onTestsChanged: (String, String) -> Unit,
    onAmountChanged: (String, String) -> Unit,
    onToggleCrossedOut: (String, Boolean) -> Unit,
    onDeleteRow: (String) -> Unit,
    onAddRow: () -> Unit,
    onRemoveGroup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, LocalExtendedColors.current.cardBorder, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "📅 Date${if (totalGroups > 1) " ${groupIndex + 1}" else ""}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            OutlinedTextField(
                value = group.date,
                onValueChange = onDateChanged,
                placeholder = { Text("e.g. 7/3/2026") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            )
            ConfidenceBadge(group.dateConfidence)
            if (totalGroups > 1) {
                IconButton(onClick = onRemoveGroup) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove group")
                }
            }
        }

        group.patients.forEachIndexed { index, patient ->
            PatientEditCard(
                patient = patient,
                index = index,
                onNameChanged = { onNameChanged(patient.localId, it) },
                onAgeChanged = { onAgeChanged(patient.localId, it) },
                onGenderChanged = { onGenderChanged(patient.localId, it) },
                onTestsChanged = { onTestsChanged(patient.localId, it) },
                onAmountChanged = { onAmountChanged(patient.localId, it) },
                onToggleCrossedOut = { onToggleCrossedOut(patient.localId, it) },
                onDelete = { onDeleteRow(patient.localId) },
            )
        }

        TextButton(onClick = onAddRow) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("  Add Row")
        }
    }
}

@Composable
private fun PatientEditCard(
    patient: PatientRecord,
    index: Int,
    onNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onGenderChanged: (String) -> Unit,
    onTestsChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onToggleCrossedOut: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val conf = patient.confidence
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (patient.crossedOut) 0.5f else 1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "#${patient.serial ?: index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConfidenceTextField(
                value = patient.name,
                onValueChange = { onNameChanged(it.uppercase()) },
                label = "Patient name",
                confidence = conf.name,
                enabled = !patient.crossedOut,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete row", tint = MaterialTheme.colorScheme.error)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfidenceTextField(
                value = patient.age,
                onValueChange = onAgeChanged,
                label = "Age",
                confidence = conf.age,
                enabled = !patient.crossedOut,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            GenderSelector(
                selected = patient.gender,
                onSelect = onGenderChanged,
                enabled = !patient.crossedOut,
                modifier = Modifier.weight(1f),
            )
            ConfidenceTextField(
                value = patient.amount ?: "",
                onValueChange = onAmountChanged,
                label = "Amount",
                confidence = conf.amount,
                isInvalid = patient.hasInvalidAmount,
                enabled = !patient.crossedOut,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        ConfidenceTextField(
            value = patient.tests,
            onValueChange = { onTestsChanged(it.uppercase()) },
            label = "Tests (CBC, TSH, LFT...)",
            confidence = conf.tests,
            enabled = !patient.crossedOut,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = patient.crossedOut, onCheckedChange = onToggleCrossedOut)
            Text("Skip this entry (crossed-out)", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(WarningLight, "Low confidence")
        LegendItem(DangerLight, "Invalid value")
        LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, "Crossed-out (skipped)")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
