package com.averykarlin.averytask.ui.screens.medication

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averykarlin.averytask.data.local.entity.SelfCareStepEntity
import com.averykarlin.averytask.data.repository.MedStepLog
import com.averykarlin.averytask.domain.model.RoutineTier
import com.averykarlin.averytask.domain.model.SelfCareRoutines
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(
    navController: NavController,
    viewModel: MedicationViewModel = hiltViewModel()
) {
    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val allSteps by viewModel.steps.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val reminderInterval by viewModel.reminderIntervalMinutes.collectAsStateWithLifecycle()

    val completedSteps = viewModel.getCompletedSteps(todayLog)
    val medStepLogs = viewModel.getMedStepLogs(todayLog)
    val logsByStepId = medStepLogs.associateBy { it.id }
    val selectedTier = viewModel.getSelectedTier(todayLog)
    val tiers = SelfCareRoutines.medicationTiers

    val doneCount = allSteps.count { it.stepId in completedSteps }
    val allDone = allSteps.isNotEmpty() && doneCount == allSteps.size
    val pct = if (allSteps.isNotEmpty()) doneCount.toFloat() / allSteps.size else 0f

    val activeTier = tiers.find { it.id == selectedTier } ?: tiers.last()
    val tierColor = Color(activeTier.color)

    var showAddDialog by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }
    var deletingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }
    var loggingTier by remember { mutableStateOf<RoutineTier?>(null) }
    var unloggingTier by remember { mutableStateOf<RoutineTier?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medication", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleEditMode() }) {
                        Icon(
                            if (editMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (editMode) "Done editing" else "Edit meds",
                            tint = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (editMode) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add medication")
                        }
                    } else {
                        IconButton(onClick = { viewModel.resetToday() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Daily medications",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (editMode) {
                    Text(
                        text = "Tap a medication to edit \u2022 use delete to remove",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Reminder interval setting (shown in edit mode)
            if (editMode) {
                item {
                    var intervalText by remember(reminderInterval) {
                        mutableStateOf(if (reminderInterval > 0) reminderInterval.toString() else "")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminder interval",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (reminderInterval > 0) "Remind ${reminderInterval} min after logging"
                                else "No reminder after logging",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { raw ->
                                intervalText = raw.filter { c -> c.isDigit() }
                                val mins = intervalText.toIntOrNull() ?: 0
                                viewModel.setReminderInterval(mins)
                            },
                            label = { Text("Min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(90.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Tier selector — tap to log/unlog a tier
            if (!editMode) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        tiers.forEach { tier ->
                            val color = Color(tier.color)
                            val tierLogged = viewModel.isTierFullyLogged(allSteps, completedSteps, tier.id)
                            val cumulativeSteps = viewModel.getStepsForTier(allSteps, tier.id)
                            val exactSteps = viewModel.getStepsInExactTier(allSteps, tier.id)
                            val hasSteps = cumulativeSteps.isNotEmpty()
                            val loggedCount = cumulativeSteps.count { it.stepId in completedSteps }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (tierLogged) Modifier
                                            .background(color.copy(alpha = 0.12f))
                                            .border(2.dp, color, RoundedCornerShape(16.dp))
                                        else Modifier
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(16.dp)
                                            )
                                    )
                                    .clickable(enabled = hasSteps) {
                                        if (tierLogged) {
                                            unloggingTier = tier
                                        } else {
                                            loggingTier = tier
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (tierLogged) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(color),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(2.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tier.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tierLogged) color else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (tierLogged) "All ${cumulativeSteps.size} meds logged"
                                            else "${cumulativeSteps.size} meds (${exactSteps.size} in this tier)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (tierLogged) color.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!tierLogged && loggedCount > 0) {
                                        Text(
                                            text = "$loggedCount/${cumulativeSteps.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Progress bar
                item {
                    val progressColor by animateColorAsState(
                        targetValue = if (allDone) Color(0xFF10B981) else tierColor,
                        animationSpec = tween(300),
                        label = "progressColor"
                    )
                    val animatedProgress by animateFloatAsState(
                        targetValue = pct,
                        animationSpec = tween(400),
                        label = "progress"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$doneCount / ${allSteps.size} meds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        if (allDone) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All meds taken \u2014 nice work.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Medication list — show all meds as reference, grouped by tier
            val displaySteps = allSteps
            if (displaySteps.isNotEmpty()) {
                item {
                    Text(
                        text = "MEDICATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(displaySteps, key = { "med_${it.stepId}_${it.id}" }) { step ->
                    val done = step.stepId in completedSteps
                    val stepTier = tiers.find { it.id == step.tier } ?: tiers.last()
                    val stepColor = Color(stepTier.color)
                    val stepIndex = allSteps.indexOf(step)

                    if (editMode) {
                        EditableMedItem(
                            step = step,
                            tierLabel = step.tier.first().uppercaseChar().toString(),
                            tierColor = stepColor,
                            isFirst = stepIndex == 0,
                            isLast = stepIndex == allSteps.lastIndex,
                            onMoveUp = { viewModel.moveStep(step, -1) },
                            onMoveDown = { viewModel.moveStep(step, 1) },
                            onEdit = { editingStep = step },
                            onDelete = { deletingStep = step }
                        )
                    } else {
                        val stepLog = logsByStepId[step.stepId]
                        MedItem(
                            label = step.label,
                            duration = step.duration,
                            note = step.note,
                            tierLabel = step.tier.first().uppercaseChar().toString(),
                            tierColor = stepColor,
                            isDone = done,
                            logEntry = stepLog,
                            onClick = if (done) {
                                { viewModel.toggleStep(step.stepId) }
                            } else null
                        )
                    }
                }
            }

            // Empty state
            if (allSteps.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "\uD83D\uDC8A",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No medications added yet",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the edit button to add your medications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add medication dialog
    if (showAddDialog) {
        MedDialog(
            title = "Add Medication",
            onDismiss = { showAddDialog = false },
            onConfirm = { label, duration, tier, note ->
                viewModel.addStep(label, duration, tier, note)
                showAddDialog = false
            }
        )
    }

    // Edit medication dialog
    editingStep?.let { step ->
        MedDialog(
            title = "Edit Medication",
            initialLabel = step.label,
            initialDuration = step.duration,
            initialTier = step.tier,
            initialNote = step.note,
            onDismiss = { editingStep = null },
            onConfirm = { label, duration, tier, note ->
                viewModel.updateStep(
                    step.copy(label = label, duration = duration, tier = tier, note = note)
                )
                editingStep = null
            }
        )
    }

    // Delete confirmation
    deletingStep?.let { step ->
        AlertDialog(
            onDismissRequest = { deletingStep = null },
            title = { Text("Remove Medication") },
            text = { Text("Remove \"${step.label}\" from your medication list?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStep(step)
                    deletingStep = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStep = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Log tier dialog
    loggingTier?.let { tier ->
        val stepsToLog = viewModel.getStepsForTier(allSteps, tier.id)
            .filter { it.stepId !in completedSteps }
        LogTierDialog(
            tier = tier,
            stepsToLog = stepsToLog,
            onDismiss = { loggingTier = null },
            onConfirm = { note ->
                viewModel.logTier(tier.id, note)
                loggingTier = null
            }
        )
    }

    // Unlog tier confirmation
    unloggingTier?.let { tier ->
        AlertDialog(
            onDismissRequest = { unloggingTier = null },
            title = { Text("Unlog ${tier.label}") },
            text = { Text("Remove logs for all ${tier.label} medications?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unlogTier(tier.id)
                    unloggingTier = null
                }) {
                    Text("Unlog", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { unloggingTier = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LogTierDialog(
    tier: RoutineTier,
    stepsToLog: List<SelfCareStepEntity>,
    onDismiss: () -> Unit,
    onConfirm: (note: String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    val tierColor = Color(tier.color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ${tier.label} meds") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Logging at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stepsToLog.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(tierColor.copy(alpha = 0.06f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stepsToLog.forEach { step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(tierColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = step.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (step.duration.isNotEmpty()) {
                                    Text(
                                        text = step.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "All ${tier.label} meds already logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. taken with food") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim()) }) {
                Text("Log ${stepsToLog.size} med${if (stepsToLog.size != 1) "s" else ""}")
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
private fun MedDialog(
    title: String,
    initialLabel: String = "",
    initialDuration: String = "",
    initialTier: String = "essential",
    initialNote: String = "",
    onDismiss: () -> Unit,
    onConfirm: (label: String, duration: String, tier: String, note: String) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var duration by remember { mutableStateOf(initialDuration) }
    var tier by remember { mutableStateOf(initialTier) }
    var note by remember { mutableStateOf(initialNote) }

    val tiers = SelfCareRoutines.medicationTiers
    var tierExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Medication name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Dosage / timing") },
                    placeholder = { Text("e.g. 20mg, morning") },
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
                        label = { Text("Session") },
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
                    onConfirm(label.trim(), duration.trim(), tier, note.trim())
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
private fun EditableMedItem(
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
            )
            .clickable(onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp), enabled = !isFirst) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    modifier = Modifier.size(18.dp),
                    tint = if (!isFirst) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                )
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp), enabled = !isLast) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    modifier = Modifier.size(18.dp),
                    tint = if (!isLast) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
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
private fun MedItem(
    label: String,
    duration: String,
    note: String,
    tierLabel: String,
    tierColor: Color,
    isDone: Boolean,
    logEntry: MedStepLog?,
    onClick: (() -> Unit)?
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDone) tierColor.copy(alpha = 0.07f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (isDone) tierColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
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
    }
}
