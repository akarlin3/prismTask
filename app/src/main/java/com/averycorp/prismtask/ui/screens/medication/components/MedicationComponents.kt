package com.averycorp.prismtask.ui.screens.medication.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.repository.MedStepLog
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
internal fun formatTime24to12(time: String): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", displayHour, minute, amPm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = 8,
        initialMinute = 0,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedDialog(
    title: String,
    initialLabel: String = "",
    initialDuration: String = "",
    initialTier: String = "essential",
    initialNote: String = "",
    initialTimeOfDay: String = "morning",
    onDismiss: () -> Unit,
    onConfirm: (label: String, duration: String, tier: String, note: String, timeOfDay: String) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var duration by remember { mutableStateOf(initialDuration) }
    var tier by remember { mutableStateOf(initialTier) }
    var note by remember { mutableStateOf(initialNote) }
    var selectedTimes by remember {
        mutableStateOf(SelfCareRoutines.parseTimeOfDay(initialTimeOfDay))
    }

    // Exclude "skipped" — it's a per-time choice, not a medication category.
    val tiers = SelfCareRoutines.medicationTiers.filter { it.id != "skipped" }
    var tierExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Medication Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Dosage") },
                    placeholder = { Text("e.g. 20mg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Tier dropdown
                ExposedDropdownMenuBox(
                    expanded = tierExpanded,
                    onExpandedChange = { tierExpanded = it }
                ) {
                    OutlinedTextField(
                        value = tiers.find { it.id == tier }?.label ?: tier,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tierExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = tierExpanded,
                        onDismissRequest = { tierExpanded = false }
                    ) {
                        tiers.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = {
                                    tier = t.id
                                    tierExpanded = false
                                }
                            )
                        }
                    }
                }
                // Time of day multi-select
                Text(
                    text = "Time of day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelfCareRoutines.timesOfDay.forEach { tod ->
                        val selected = tod.id in selectedTimes
                        val chipColor = Color(tod.color)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) {
                                        chipColor.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    }
                                ).border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) chipColor else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ).clickable {
                                    selectedTimes = if (selected) {
                                        selectedTimes - tod.id
                                    } else {
                                        selectedTimes + tod.id
                                    }
                                }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tod.icon,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = tod.label.take(4),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) chipColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. take with food") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val timeOfDay = SelfCareRoutines
                        .serializeTimeOfDay(selectedTimes)
                        .ifEmpty { "morning" }
                    onConfirm(label.trim(), duration.trim(), tier, note.trim(), timeOfDay)
                },
                enabled = label.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun EditableMedItem(
    step: SelfCareStepEntity,
    tierLabel: String,
    tierColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ).clickable(onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp), enabled = !isFirst) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    modifier = Modifier.size(18.dp),
                    tint = if (!isFirst) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    }
                )
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp), enabled = !isLast) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    modifier = Modifier.size(18.dp),
                    tint = if (!isLast) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (step.note.isNotEmpty()) {
                Text(
                    text = step.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (step.duration.isNotEmpty()) {
            Text(
                text = step.duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tierColor.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tierColor
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun MedItem(
    label: String,
    duration: String,
    note: String,
    tierLabel: String,
    tierColor: Color,
    isDone: Boolean,
    logEntry: MedStepLog?,
    onUnlog: (() -> Unit)?
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDone) {
                    tierColor.copy(alpha = 0.07f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ).border(
                width = 1.dp,
                color = if (isDone) tierColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isDone) tierColor else tierColor.copy(alpha = 0.3f))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (logEntry != null && logEntry.at > 0) {
                val logText = buildString {
                    append("Logged at ${timeFormat.format(Date(logEntry.at))}")
                    if (logEntry.note.isNotEmpty()) append(" \u2014 ${logEntry.note}")
                }
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tierColor.copy(alpha = 0.8f)
                )
            } else if (note.isNotEmpty()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (duration.isNotEmpty()) {
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tierColor.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tierColor
            )
        }
        if (isDone && onUnlog != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onUnlog,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Unlog",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
