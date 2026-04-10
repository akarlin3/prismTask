package com.averycorp.prismtask.ui.screens.medication

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.repository.MedStepLog
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
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
    val scheduleMode by viewModel.scheduleMode.collectAsStateWithLifecycle()
    val specificTimes by viewModel.specificTimes.collectAsStateWithLifecycle()

    val medStepLogs = viewModel.getMedStepLogs(todayLog)
    val tiers = SelfCareRoutines.medicationTiers
    val tiersByTime = viewModel.getTiersByTime(todayLog)

    // Count how many times-of-day have been "checked" — meaning the user has
    // picked a tier for that block (essential, prescription, complete, or
    // skipped). Only time-of-day groups that actually have meds scheduled
    // count toward the total.
    val timeGroupIds = SelfCareRoutines.timesOfDay
        .filter { tod -> allSteps.any { step -> tod.id in SelfCareRoutines.parseTimeOfDay(step.timeOfDay) } }
        .map { it.id }
    val timesTotal = timeGroupIds.size
    val timesChecked = timeGroupIds.count { it in tiersByTime.keys }
    val allDone = timesTotal > 0 && timesChecked == timesTotal
    val pct = if (timesTotal > 0) timesChecked.toFloat() / timesTotal else 0f

    var showAddDialog by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }
    var deletingStep by remember { mutableStateOf<SelfCareStepEntity?>(null) }

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
                    if (!editMode) {
                        IconButton(onClick = {
                            navController.navigate(PrismTaskRoute.MedicationLog.route)
                        }) {
                            Icon(Icons.Default.History, contentDescription = "Medication log")
                        }
                    }
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
                    text = "Daily Medications",
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

            // Reminder scheduling settings (shown in edit mode)
            if (editMode) {
                item {
                    var showTimePicker by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Reminder Schedule",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Mode toggle chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = scheduleMode == MedicationScheduleMode.INTERVAL,
                                onClick = { viewModel.setScheduleMode(MedicationScheduleMode.INTERVAL) },
                                label = { Text("Interval") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                            FilterChip(
                                selected = scheduleMode == MedicationScheduleMode.SPECIFIC_TIMES,
                                onClick = { viewModel.setScheduleMode(MedicationScheduleMode.SPECIFIC_TIMES) },
                                label = { Text("Specific Times") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        // Interval mode UI
                        if (scheduleMode == MedicationScheduleMode.INTERVAL) {
                            var intervalText by remember(reminderInterval) {
                                mutableStateOf(if (reminderInterval > 0) reminderInterval.toString() else "")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (reminderInterval > 0) "Remind ${reminderInterval} min after logging"
                                    else "No reminder after logging",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
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
                        }

                        // Specific times mode UI
                        if (scheduleMode == MedicationScheduleMode.SPECIFIC_TIMES) {
                            Text(
                                text = "Set exact times to be reminded each day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sortedTimes = specificTimes.sorted().toList()
                            if (sortedTimes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sortedTimes.forEach { time ->
                                        InputChip(
                                            selected = false,
                                            onClick = { viewModel.removeSpecificTime(time) },
                                            label = { Text(formatTime24to12(time)) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove $time",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = InputChipDefaults.inputChipColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = { showTimePicker = true }
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Time")
                            }
                        }
                    }

                    // Time picker dialog
                    if (showTimePicker) {
                        TimePickerDialog(
                            onDismiss = { showTimePicker = false },
                            onConfirm = { hour, minute ->
                                val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
                                viewModel.addSpecificTime(timeStr)
                                showTimePicker = false
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Progress bar
            if (!editMode) {
                item {
                    // Neutral color until the user has actually checked at least
                    // one time-of-day; no default tier color is applied.
                    val neutralColor = MaterialTheme.colorScheme.primary
                    val progressColor by animateColorAsState(
                        targetValue = if (allDone) Color(0xFF10B981) else neutralColor,
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
                                text = "$timesChecked / $timesTotal times of day",
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
                                text = "All Times Checked \u2014 Nice Work.",
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

            // Medication list — grouped by time of day
            val displaySteps = allSteps
            if (displaySteps.isNotEmpty()) {
                val timeGroups = SelfCareRoutines.timesOfDay.mapNotNull { tod ->
                    val stepsInTime = displaySteps.filter { step ->
                        tod.id in SelfCareRoutines.parseTimeOfDay(step.timeOfDay)
                    }
                    if (stepsInTime.isNotEmpty()) tod to stepsInTime else null
                }
                // Meds with no recognized time-of-day (legacy data)
                val allGroupedStepIds = timeGroups.flatMap { (_, steps) -> steps.map { it.id } }.toSet()
                val ungrouped = displaySteps.filter { it.id !in allGroupedStepIds }

                timeGroups.forEach { (tod, stepsInGroup) ->
                    item(key = "header_${tod.id}") {
                        val todColor = Color(tod.color)
                        Column(modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tod.icon,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tod.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = todColor,
                                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f
                                )
                            }
                            if (!editMode) {
                                val pickedTier = tiersByTime[tod.id]
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    tiers.chunked(2).forEach { rowTiers ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowTiers.forEach { tier ->
                                                val tierC = Color(tier.color)
                                                val isPicked = pickedTier == tier.id
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (isPicked) tierC.copy(alpha = 0.18f)
                                                            else MaterialTheme.colorScheme.surfaceContainerLow
                                                        )
                                                        .border(
                                                            width = if (isPicked) 1.5.dp else 1.dp,
                                                            color = if (isPicked) tierC
                                                            else MaterialTheme.colorScheme.outlineVariant,
                                                            shape = RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable {
                                                            viewModel.setTierForTime(
                                                                tod.id,
                                                                if (isPicked) null else tier.id
                                                            )
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .then(
                                                                    if (isPicked) Modifier.background(tierC)
                                                                    else Modifier.border(
                                                                        1.5.dp,
                                                                        tierC.copy(alpha = 0.5f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isPicked) {
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = tier.label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isPicked) tierC
                                                            else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            // Fill any empty slots in the last row for odd tier counts.
                                            repeat(2 - rowTiers.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(stepsInGroup, key = { "med_${tod.id}_${it.stepId}_${it.id}" }) { step ->
                        val done = viewModel.isStepDoneAt(medStepLogs, step.stepId, tod.id)
                        val stepTier = tiers.find { it.id == step.tier }
                            ?: tiers.find { it.id == "complete" }
                            ?: tiers.first()
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
                            val stepLog = viewModel.getLogForStepAt(medStepLogs, step.stepId, tod.id)
                            MedItem(
                                label = step.label,
                                duration = step.duration,
                                note = step.note,
                                tierLabel = step.tier.first().uppercaseChar().toString(),
                                tierColor = stepColor,
                                isDone = done,
                                logEntry = stepLog,
                                onUnlog = if (done) {
                                    { viewModel.toggleStep(step.stepId, tod.id) }
                                } else null
                            )
                        }
                    }
                }

                // Show ungrouped meds (legacy without valid time_of_day)
                if (ungrouped.isNotEmpty()) {
                    item(key = "header_other") {
                        Text(
                            text = "OTHER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                    }
                    items(ungrouped, key = { "med_other_${it.stepId}_${it.id}" }) { step ->
                        val done = medStepLogs.any { it.id == step.stepId }
                        val stepTier = tiers.find { it.id == step.tier }
                            ?: tiers.find { it.id == "complete" }
                            ?: tiers.first()
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
                            val stepLog = medStepLogs.firstOrNull { it.id == step.stepId }
                            MedItem(
                                label = step.label,
                                duration = step.duration,
                                note = step.note,
                                tierLabel = step.tier.first().uppercaseChar().toString(),
                                tierColor = stepColor,
                                isDone = done,
                                logEntry = stepLog,
                                onUnlog = if (done) {
                                    { viewModel.toggleStep(step.stepId) }
                                } else null
                            )
                        }
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
            onConfirm = { label, duration, tier, note, timeOfDay ->
                viewModel.addStep(label, duration, tier, note, timeOfDay)
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
            initialTimeOfDay = step.timeOfDay,
            onDismiss = { editingStep = null },
            onConfirm = { label, duration, tier, note, timeOfDay ->
                viewModel.updateStep(
                    step.copy(label = label, duration = duration, tier = tier, note = note, timeOfDay = timeOfDay)
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

}

/** Convert "HH:mm" (24h) to "h:mm a" (12h) for display. */
private fun formatTime24to12(time: String): String {
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
private fun TimePickerDialog(
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
private fun MedDialog(
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
                                    if (selected) chipColor.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) chipColor else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedTimes = if (selected) {
                                        selectedTimes - tod.id
                                    } else {
                                        selectedTimes + tod.id
                                    }
                                }
                                .padding(vertical = 8.dp),
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
                    val timeOfDay = SelfCareRoutines.serializeTimeOfDay(selectedTimes)
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
    onUnlog: (() -> Unit)?
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
