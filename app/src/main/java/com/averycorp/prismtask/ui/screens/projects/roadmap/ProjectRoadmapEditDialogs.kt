package com.averycorp.prismtask.ui.screens.projects.roadmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.local.entity.ExternalAnchorEntity
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.ComparisonOp
import com.averycorp.prismtask.domain.model.ExternalAnchor

/**
 * Editor dialogs for the project-roadmap surface (PrismTask-timeline-class
 * scope, PR-2). Every dialog is a thin form over an existing repo write
 * path — validation lives in the [ProjectRoadmapViewModel] so ViewModels
 * stay testable without instantiating Compose.
 *
 * AlertDialog is used uniformly so the editors feel consistent with the
 * codebase's existing delete-confirmation pattern. Anchor's variant
 * picker is the only structurally complex form; everything else is a
 * one-or-two-field write.
 */
@Composable
fun PhaseEditDialog(
    existing: ProjectPhaseEntity?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String?,
        startDate: Long?,
        endDate: Long?,
        versionAnchor: String?
    ) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var versionAnchor by remember { mutableStateOf(existing?.versionAnchor ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Phase" else "Edit Phase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = versionAnchor,
                    onValueChange = { versionAnchor = it },
                    label = { Text("Version Anchor (e.g. v1.9.0)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Date range left as-is on the existing row; this dialog
                // intentionally doesn't expose a date picker to keep the
                // first-cut editor focused on text fields. Date editing
                // can be added as a follow-on once the picker pattern
                // stabilizes elsewhere in the codebase.
                onSave(
                    title,
                    description,
                    existing?.startDate,
                    existing?.endDate,
                    versionAnchor
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RiskEditDialog(
    existing: ProjectRiskEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, level: String, mitigation: String?) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var level by remember { mutableStateOf(existing?.level ?: "MEDIUM") }
    var mitigation by remember { mutableStateOf(existing?.mitigation ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Risk" else "Edit Risk") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                LevelDropdown(level) { level = it }
                OutlinedTextField(
                    value = mitigation,
                    onValueChange = { mitigation = it },
                    label = { Text("Mitigation (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, level, mitigation) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LevelDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = { expanded = true }) { Text("Severity: $current") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("LOW", "MEDIUM", "HIGH").forEach { lvl ->
                DropdownMenuItem(
                    text = { Text(lvl) },
                    onClick = {
                        onSelect(lvl)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorEditDialog(
    existing: ExternalAnchorEntity?,
    decoded: ExternalAnchor?,
    onDismiss: () -> Unit,
    onSave: (label: String, anchor: ExternalAnchor) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var variant by remember { mutableStateOf(decoded.toVariantKey()) }
    // Per-variant editable fields. We keep them all materialized so
    // the user can flip between variants without losing prior state.
    var dateMs by remember {
        mutableStateOf(
            (decoded as? ExternalAnchor.CalendarDeadline)?.epochMs?.toString() ?: ""
        )
    }
    var metric by remember {
        mutableStateOf((decoded as? ExternalAnchor.NumericThreshold)?.metric ?: "")
    }
    var op by remember {
        mutableStateOf((decoded as? ExternalAnchor.NumericThreshold)?.op ?: ComparisonOp.LT)
    }
    var value by remember {
        mutableStateOf(
            (decoded as? ExternalAnchor.NumericThreshold)?.value?.toString() ?: ""
        )
    }
    var gateKey by remember {
        mutableStateOf((decoded as? ExternalAnchor.BooleanGate)?.gateKey ?: "")
    }
    var expectedState by remember {
        mutableStateOf((decoded as? ExternalAnchor.BooleanGate)?.expectedState ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add External Anchor" else "Edit Anchor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                VariantPicker(variant) { variant = it }
                when (variant) {
                    "DATE" -> OutlinedTextField(
                        value = dateMs,
                        onValueChange = { dateMs = it },
                        label = { Text("Deadline (epoch ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    "METRIC" -> {
                        OutlinedTextField(
                            value = metric,
                            onValueChange = { metric = it },
                            label = { Text("Metric Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OpPicker(op) { op = it }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text("Threshold Value") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "GATE" -> {
                        OutlinedTextField(
                            value = gateKey,
                            onValueChange = { gateKey = it },
                            label = { Text("Gate Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = { expectedState = !expectedState }) {
                                Text("Expected: $expectedState (tap to flip)")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val anchor = when (variant) {
                    "DATE" -> dateMs.toLongOrNull()?.let { ExternalAnchor.CalendarDeadline(it) }
                    "METRIC" -> value.toDoubleOrNull()?.let {
                        ExternalAnchor.NumericThreshold(metric, op, it)
                    }
                    "GATE" -> ExternalAnchor.BooleanGate(gateKey, expectedState)
                    else -> null
                } ?: return@TextButton
                onSave(label, anchor)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun ExternalAnchor?.toVariantKey(): String = when (this) {
    is ExternalAnchor.CalendarDeadline -> "DATE"
    is ExternalAnchor.NumericThreshold -> "METRIC"
    is ExternalAnchor.BooleanGate -> "GATE"
    null -> "DATE"
}

@Composable
private fun VariantPicker(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("Type: $current") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf("DATE", "METRIC", "GATE").forEach { v ->
            DropdownMenuItem(
                text = { Text(v) },
                onClick = {
                    onSelect(v)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun OpPicker(current: ComparisonOp, onSelect: (ComparisonOp) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("Op: ${current.symbol}") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ComparisonOp.entries.forEach { o ->
            DropdownMenuItem(
                text = { Text(o.symbol) },
                onClick = {
                    onSelect(o)
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun DependencyAddDialog(
    projectTasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onSave: (blockerId: Long, blockedId: Long) -> Unit
) {
    var blockerId by remember { mutableStateOf(projectTasks.firstOrNull()?.id ?: 0L) }
    var blockedId by remember { mutableStateOf(projectTasks.getOrNull(1)?.id ?: 0L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Dependency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Blocker (must finish first):")
                TaskPicker(projectTasks, blockerId) { blockerId = it }
                Text("Blocked (waits on blocker):")
                TaskPicker(projectTasks, blockedId) { blockedId = it }
                if (blockerId == blockedId && blockerId != 0L) {
                    Text(
                        "A task can't block itself.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(blockerId, blockedId) },
                enabled = blockerId != 0L && blockedId != 0L && blockerId != blockedId
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TaskPicker(
    projectTasks: List<TaskEntity>,
    currentId: Long,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentTitle = projectTasks.firstOrNull { it.id == currentId }?.title ?: "(pick a task)"
    TextButton(onClick = { expanded = true }) { Text(currentTitle) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        projectTasks.forEach { t ->
            DropdownMenuItem(
                text = { Text(t.title) },
                onClick = {
                    onSelect(t.id)
                    expanded = false
                }
            )
        }
    }
}
