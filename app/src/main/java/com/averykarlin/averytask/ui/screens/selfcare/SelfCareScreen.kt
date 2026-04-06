package com.averykarlin.averytask.ui.screens.selfcare

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averykarlin.averytask.data.local.entity.SelfCareStepEntity
import com.averykarlin.averytask.domain.model.SelfCareRoutines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfCareScreen(
    navController: NavController,
    viewModel: SelfCareViewModel = hiltViewModel()
) {
    val routineType by viewModel.routineType.collectAsStateWithLifecycle()
    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val allSteps by viewModel.steps.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()

    val completedSteps = viewModel.getCompletedSteps(todayLog)
    val selectedTier = viewModel.getSelectedTier(todayLog)
    val tiers = SelfCareRoutines.getTiers(routineType)
    val tierOrder = SelfCareRoutines.getTierOrder(routineType)
    val visibleSteps = viewModel.getVisibleSteps(allSteps, selectedTier)
    val phaseGroups = viewModel.getPhaseGroupedSteps(
        if (editMode) allSteps else visibleSteps
    )

    val tierTimes = viewModel.computeTierTimes(allSteps)

    val doneCount = visibleSteps.count { it.stepId in completedSteps }
    val allDone = visibleSteps.isNotEmpty() && doneCount == visibleSteps.size
    val pct = if (visibleSteps.isNotEmpty()) doneCount.toFloat() / visibleSteps.size else 0f

    val activeTier = tiers.find { it.id == selectedTier } ?: tiers.first()
    val tierColor = Color(activeTier.color)

    val tabIndex = if (routineType == "morning") 0 else 1

    var showAddDialog by remember { mutableStateOf(false) }
    var showPhaseOrderDialog by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }
    var deletingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Self-Care", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleEditMode() }) {
                        Icon(
                            if (editMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (editMode) "Done editing" else "Edit steps",
                            tint = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (editMode) {
                        IconButton(onClick = { showPhaseOrderDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Reorder phases")
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add step")
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
            // Tab row
            item {
                TabRow(
                    selectedTabIndex = tabIndex,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { viewModel.switchRoutine("morning") },
                        text = { Text("Morning") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { viewModel.switchRoutine("bedtime") },
                        text = { Text("Bedtime") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Header
            item {
                Text(
                    text = if (routineType == "morning") "Self-care routine" else "Wind-down routine",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (editMode) {
                    Text(
                        text = "Tap a step to edit \u2022 swipe or use delete to remove",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tier selector
            if (!editMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tiers.forEach { tier ->
                            val isActive = tier.id == selectedTier
                            val color = Color(tier.color)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isActive) Modifier
                                            .background(color.copy(alpha = 0.1f))
                                            .border(2.dp, color, RoundedCornerShape(12.dp))
                                        else Modifier
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(12.dp)
                                            )
                                    )
                                    .clickable { viewModel.setTier(tier.id) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = tier.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = tierTimes[tier.id] ?: tier.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) color.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
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
                                text = "$doneCount / ${visibleSteps.size} steps",
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
                                text = if (routineType == "morning") "All done \u2014 go get it, Avery." else "All done \u2014 lights out. Sleep well, Avery.",
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

            // Phase-grouped steps
            phaseGroups.forEach { (phaseName, phaseSteps) ->
                if (phaseSteps.isNotEmpty()) {
                    item {
                        Text(
                            text = phaseName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(phaseSteps, key = { "${routineType}_${it.stepId}_${it.id}" }) { step ->
                        val done = step.stepId in completedSteps
                        val stepTier = tiers.find { it.id == step.tier } ?: tiers.first()
                        val stepColor = Color(stepTier.color)
                        val stepIndex = allSteps.indexOf(step)

                        if (editMode) {
                            EditableStepItem(
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
                            StepItem(
                                label = step.label,
                                duration = step.duration,
                                note = step.note,
                                tierLabel = step.tier.first().uppercaseChar().toString(),
                                tierColor = stepColor,
                                isDone = done,
                                onClick = { viewModel.toggleStep(step.stepId) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Survival done beats full skipped.\nConsistency > perfection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add step dialog
    if (showAddDialog) {
        StepDialog(
            title = "Add Step",
            routineType = routineType,
            onDismiss = { showAddDialog = false },
            onConfirm = { label, duration, tier, note, phase ->
                viewModel.addStep(label, duration, tier, note, phase)
                showAddDialog = false
            }
        )
    }

    // Edit step dialog
    editingStep?.let { step ->
        StepDialog(
            title = "Edit Step",
            routineType = routineType,
            initialLabel = step.label,
            initialDuration = step.duration,
            initialTier = step.tier,
            initialNote = step.note,
            initialPhase = step.phase,
            onDismiss = { editingStep = null },
            onConfirm = { label, duration, tier, note, phase ->
                viewModel.updateStep(
                    step.copy(
                        label = label,
                        duration = duration,
                        tier = tier,
                        note = note,
                        phase = phase
                    )
                )
                editingStep = null
            }
        )
    }

    // Delete confirmation
    deletingStep?.let { step ->
        AlertDialog(
            onDismissRequest = { deletingStep = null },
            title = { Text("Delete Step") },
            text = { Text("Remove \"${step.label}\" from your ${routineType} routine?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStep(step)
                    deletingStep = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStep = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Phase reorder dialog
    if (showPhaseOrderDialog) {
        PhaseOrderDialog(
            initialPhases = viewModel.getCurrentPhases(allSteps),
            onDismiss = { showPhaseOrderDialog = false },
            onConfirm = { phaseOrder ->
                viewModel.sortByPhaseOrder(phaseOrder)
                showPhaseOrderDialog = false
            }
        )
    }
}

@Composable
private fun PhaseOrderDialog(
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
                                tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
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
                                tint = if (index < phases.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
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
private fun StepDialog(
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
    val phases = if (routineType == "morning") {
        listOf("Skincare", "Hygiene", "Grooming")
    } else {
        listOf("Wash", "Skincare", "Hygiene", "Sleep")
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
private fun EditableStepItem(
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
        // Reorder buttons
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

        Text(
            text = step.duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
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
private fun StepItem(
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
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (isDone) Modifier.background(tierColor)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
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
