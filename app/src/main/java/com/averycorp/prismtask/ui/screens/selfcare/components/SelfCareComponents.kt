package com.averycorp.prismtask.ui.screens.selfcare.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.ui.theme.LocalPrismShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhaseOrderDialog(
    initialPhases: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var phases by remember { mutableStateOf(initialPhases) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder Phases") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Steps will be sorted into this phase order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                phases.forEachIndexed { index, phase ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = phase,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    phases = phases.toMutableList().apply {
                                        add(index - 1, removeAt(index))
                                    }
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(20.dp),
                                tint = if (index > 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < phases.lastIndex) {
                                    phases = phases.toMutableList().apply {
                                        add(index + 1, removeAt(index))
                                    }
                                }
                            },
                            enabled = index < phases.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(20.dp),
                                tint = if (index < phases.lastIndex) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(phases) }) {
                Text("Apply")
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
internal fun StepDialog(
    title: String,
    routineType: String,
    initialLabel: String = "",
    initialDuration: String = "",
    initialTier: String = "",
    initialNote: String = "",
    initialPhase: String = "",
    onDismiss: () -> Unit,
    onConfirm: (label: String, duration: String, tier: String, note: String, phase: String) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var duration by remember { mutableStateOf(initialDuration) }
    var tier by remember { mutableStateOf(initialTier) }
    var note by remember { mutableStateOf(initialNote) }
    var phase by remember { mutableStateOf(initialPhase) }

    val tiers = SelfCareRoutines.getTiers(routineType)
    val phases = when (routineType) {
        "morning" -> listOf("Skincare", "Hygiene", "Grooming")
        "housework" -> listOf("Kitchen", "Living Areas", "Bathroom", "Laundry")
        else -> listOf("Wash", "Skincare", "Hygiene", "Sleep")
    }

    var tierExpanded by remember { mutableStateOf(false) }
    var phaseExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration") },
                    placeholder = { Text("e.g. ~2 min") },
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
                        label = { Text("Tier") },
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
                // Phase dropdown
                ExposedDropdownMenuBox(
                    expanded = phaseExpanded,
                    onExpandedChange = { phaseExpanded = it }
                ) {
                    OutlinedTextField(
                        value = phase,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Phase") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = phaseExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = phaseExpanded,
                        onDismissRequest = { phaseExpanded = false }
                    ) {
                        phases.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    phase = p
                                    phaseExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim(), duration.trim(), tier, note.trim(), phase) },
                enabled = label.isNotBlank() && duration.isNotBlank() && tier.isNotBlank() && phase.isNotBlank()
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
internal fun EditableStepItem(
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
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            ).clickable(onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reorder buttons
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

        Text(
            text = step.duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(LocalPrismShapes.current.chip)
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
internal fun StepItem(
    label: String,
    duration: String,
    note: String,
    tierLabel: String,
    tierColor: Color,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isDone) {
                    tierColor.copy(alpha = 0.07f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ).border(
                width = 1.dp,
                color = if (isDone) tierColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            ).clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (isDone) {
                        Modifier.background(tierColor)
                    } else {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label + note
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
            if (note.isNotEmpty()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration + tier badge
        Text(
            text = duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(LocalPrismShapes.current.chip)
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
    }
}
