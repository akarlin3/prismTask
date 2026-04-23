package com.averycorp.prismtask.ui.screens.settings.sections.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Inline editor used by `MedicationSlotsScreen` for both create and edit.
 * Shared because the two flows have identical fields and validation; only
 * the dialog title and the "save / create" button label differ.
 *
 * Drift presets cover the common product asks (±30/60/120/180 min) plus a
 * "Custom" override that opens a numeric field. The picker is a wall-clock
 * hh:mm string editor — we deliberately avoid Material's `TimePicker` here
 * because the existing medication time inputs in `MedicationComponents.kt`
 * use the same plain `OutlinedTextField` pattern, and consistency wins
 * over the slightly nicer popup picker for now.
 */
@Composable
internal fun MedicationSlotEditorSheet(
    title: String,
    initialName: String,
    initialIdealTime: String,
    initialDriftMinutes: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, idealTime: String, driftMinutes: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var idealTime by remember { mutableStateOf(initialIdealTime) }
    var driftMinutes by remember { mutableStateOf(initialDriftMinutes) }
    var customDriftText by remember(driftMinutes) {
        mutableStateOf(driftMinutes.toString())
    }
    val driftPresets = listOf(30, 60, 120, 180)
    val isCustomDrift = driftMinutes !in driftPresets

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Slot Name") },
                    placeholder = { Text("e.g. Morning") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = idealTime,
                    onValueChange = { raw -> idealTime = sanitizeHhMm(raw, idealTime) },
                    label = { Text("Ideal Time (HH:mm)") },
                    placeholder = { Text("09:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Drift Window",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    driftPresets.forEach { mins ->
                        val selected = !isCustomDrift && driftMinutes == mins
                        AssistChip(
                            onClick = { driftMinutes = mins },
                            label = { Text(formatDrift(mins)) },
                            colors = if (selected) {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                AssistChipDefaults.assistChipColors()
                            }
                        )
                    }
                    AssistChip(
                        onClick = {
                            // Switch to custom mode by nudging value off a preset.
                            if (!isCustomDrift) {
                                driftMinutes = (driftMinutes + 1).coerceAtLeast(1)
                                customDriftText = driftMinutes.toString()
                            }
                        },
                        label = { Text("Custom") },
                        colors = if (isCustomDrift) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        }
                    )
                }
                if (isCustomDrift) {
                    OutlinedTextField(
                        value = customDriftText,
                        onValueChange = { raw ->
                            customDriftText = raw.filter { it.isDigit() }.take(4)
                            customDriftText.toIntOrNull()?.let { mins ->
                                driftMinutes = mins.coerceIn(1, 1440)
                            }
                        },
                        label = { Text("Custom drift (minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A drift window of ${formatDrift(driftMinutes)} means a dose " +
                        "counts as on-time if logged within ${formatDrift(driftMinutes)} of $idealTime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), idealTime, driftMinutes) },
                enabled = name.isNotBlank() && isValidHhMm(idealTime) && driftMinutes >= 1,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatDrift(mins: Int): String = "±${mins}m"

private fun isValidHhMm(value: String): Boolean {
    val parts = value.split(":")
    if (parts.size != 2) return false
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

/**
 * Best-effort `HH:mm` shape coercion. Strips non-digit/non-colon and
 * caps lengths so the user can type freely without the field rejecting
 * keystrokes mid-edit. Returns the previous value when the new input is
 * obviously past the maximum length.
 */
private fun sanitizeHhMm(raw: String, prev: String): String {
    val filtered = raw.filter { it.isDigit() || it == ':' }
    if (filtered.length > 5) return prev
    return filtered
}
