package com.averycorp.prismtask.ui.screens.selfcare

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.ui.screens.selfcare.components.EditableStepItem
import com.averycorp.prismtask.ui.screens.selfcare.components.PhaseOrderDialog
import com.averycorp.prismtask.ui.screens.selfcare.components.StepDialog
import com.averycorp.prismtask.ui.screens.selfcare.components.StepItem
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.LocalPrismShapes

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
    val tierDefaults by viewModel.tierDefaults.collectAsStateWithLifecycle()

    val completedSteps = viewModel.getCompletedSteps(todayLog)
    val selectedTier = viewModel.getSelectedTier(todayLog, tierDefaults)
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

    val isHousework = routineType == "housework"
    val tabIndex = if (routineType == "morning") 0 else 1

    var showAddDialog by remember { mutableStateOf(false) }
    var showPhaseOrderDialog by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }
    var deletingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isHousework) "Housework" else "Self-Care", fontWeight = FontWeight.Bold) },
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
            // Tab row (only for self-care morning/bedtime, not housework)
            if (!isHousework) {
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
            }

            // Header
            item {
                Text(
                    text = when (routineType) {
                        "morning" -> "Self-Care Routine"
                        "housework" -> "Housework Routine"
                        else -> "Wind-Down Routine"
                    },
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
                                    .clip(LocalPrismShapes.current.chip)
                                    .then(
                                        if (isActive) {
                                            Modifier
                                                .background(color.copy(alpha = 0.1f))
                                                .border(2.dp, color, RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    RoundedCornerShape(12.dp)
                                                )
                                        }
                                    ).clickable { viewModel.setTier(tier.id) }
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
                                        color = if (isActive) {
                                            color.copy(
                                                alpha = 0.7f
                                            )
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        }
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
                        targetValue = if (allDone) LocalPrismColors.current.successColor else tierColor,
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
                            .clip(MaterialTheme.shapes.medium)
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
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                        if (allDone) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (routineType) {
                                    "morning" -> "All done \u2014 go get it."
                                    "housework" -> "All done \u2014 house is looking great!"
                                    else -> "All done \u2014 lights out. Sleep well."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = LocalPrismColors.current.successColor,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Empty state when the user hasn't picked any starter steps.
            if (allSteps.isEmpty() && !editMode) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isHousework) "No Housework Steps Yet" else "No Routine Steps Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add starter steps from Settings \u2192 Life Modes \u2192 Browse " +
                                    "Templates, or tap the pencil then + to create your own.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
                                tierLabel = step.tier
                                    .first()
                                    .uppercaseChar()
                                    .toString(),
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
                                tierLabel = step.tier
                                    .first()
                                    .uppercaseChar()
                                    .toString(),
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
                        .clip(MaterialTheme.shapes.medium)
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
            text = { Text("Remove \"${step.label}\" from your $routineType routine?") },
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
