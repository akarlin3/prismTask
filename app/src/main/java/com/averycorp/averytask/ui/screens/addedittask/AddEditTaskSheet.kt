package com.averycorp.averytask.ui.screens.addedittask

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.averytask.domain.model.RecurrenceRule
import com.averycorp.averytask.domain.model.RecurrenceType
import com.averycorp.averytask.ui.components.RecurrenceDialog
import com.averycorp.averytask.ui.components.TagSelector
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Entry point for presenting the task editor as a modal bottom sheet from any
 * screen. Creates its own [AddEditTaskViewModel] scoped to the caller's
 * ViewModelStoreOwner (typically the containing NavBackStackEntry) and seeds
 * it with the supplied taskId / create-mode defaults.
 *
 * @param taskId existing task to edit, or null for create mode.
 * @param projectId pre-selected project for create mode (ignored in edit mode).
 * @param initialDate pre-set due date for create mode (ignored in edit mode).
 * @param initialTab tab to open first (0=Details, 1=Schedule, 2=Organize).
 * @param onDismiss invoked after the sheet has finished closing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskSheetHost(
    taskId: Long?,
    projectId: Long?,
    initialDate: Long?,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
) {
    val viewModel: AddEditTaskViewModel = hiltViewModel(key = "addedit_task_sheet")

    LaunchedEffect(taskId, projectId, initialDate) {
        viewModel.initialize(taskId = taskId, projectId = projectId, initialDate = initialDate)
    }

    AddEditTaskSheet(
        viewModel = viewModel,
        initialTab = initialTab,
        onDismiss = onDismiss
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun AddEditTaskSheet(
    viewModel: AddEditTaskViewModel,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    val titleFocusRequester = remember { FocusRequester() }

    fun attemptDismiss() {
        if (viewModel.hasUnsavedChanges) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { attemptDismiss() },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            // Sticky header: close / screen title / save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { attemptDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
                Text(
                    text = if (viewModel.isEditMode) "Edit Task" else "New Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            if (viewModel.saveTask()) onDismiss()
                        }
                    }
                ) {
                    Text(
                        text = "Save",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Title field (large, always visible above tabs)
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = {
                    Text(
                        text = "Task Title",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge,
                isError = viewModel.titleError,
                supportingText = if (viewModel.titleError) {
                    { Text("Title is required") }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(titleFocusRequester)
            )

            // Priority circles row (always visible above tabs)
            PriorityCircleRow(
                selected = viewModel.priority,
                onSelect = viewModel::onPriorityChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tab bar
            val tabs = listOf("Details", "Schedule", "Organize")
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index)
                                    FontWeight.SemiBold
                                else
                                    FontWeight.Normal
                            )
                        }
                    )
                }
            }
            HorizontalDivider()

            // Swipeable tab content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (page) {
                        0 -> DetailsTabContent(viewModel)
                        1 -> ScheduleTabContent(viewModel)
                        2 -> OrganizeTabContent(viewModel)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Auto-focus the title field when creating a new task.
    LaunchedEffect(Unit) {
        if (!viewModel.isEditMode) {
            titleFocusRequester.requestFocus()
        }
    }

    // Back button / gesture: route through attemptDismiss so unsaved changes
    // prompt for confirmation before closing.
    BackHandler { attemptDismiss() }

    // Discard confirmation dialog
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Discard",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

/**
 * Header row of 5 priority dots (None / Low / Medium / High / Urgent).
 * Tap to select; the selected dot shows a checkmark overlay.
 */
@Composable
private fun PriorityCircleRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColors = LocalPriorityColors.current
    val levels = listOf(
        0 to priorityColors.none,
        1 to priorityColors.low,
        2 to priorityColors.medium,
        3 to priorityColors.high,
        4 to priorityColors.urgent
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        levels.forEach { (level, color) ->
            PriorityCircle(
                color = color,
                selected = selected == level,
                onClick = { onSelect(level) }
            )
        }
    }
}

@Composable
private fun PriorityCircle(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tab content composables
// ---------------------------------------------------------------------------

@Composable
private fun DetailsTabContent(viewModel: AddEditTaskViewModel) {
    val context = LocalContext.current
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()

    var notesExpanded by remember { mutableStateOf(viewModel.notes.isNotBlank()) }
    var showAddLinkDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAddImageAttachment(context, it) }
    }

    // Description
    OutlinedTextField(
        value = viewModel.description,
        onValueChange = viewModel::onDescriptionChange,
        label = { Text("Description") },
        minLines = 3,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth()
    )

    // Notes (collapsible)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { notesExpanded = !notesExpanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("Notes")
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (notesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (notesExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AnimatedVisibility(visible = notesExpanded) {
        OutlinedTextField(
            value = viewModel.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text("Notes") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Attachments (only in edit mode)
    if (viewModel.isEditMode) {
        SectionLabel("Attachments")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Image")
            }
            OutlinedButton(onClick = { showAddLinkDialog = true }) {
                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Link")
            }
        }
        if (attachments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        onDelete = { viewModel.onDeleteAttachment(context, attachment) }
                    )
                }
            }
        }
    }

    // Add link dialog
    if (showAddLinkDialog) {
        var linkUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddLinkDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (linkUrl.isNotBlank()) {
                            viewModel.onAddLinkAttachment(linkUrl.trim())
                            showAddLinkDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLinkDialog = false }) { Text("Cancel") }
            },
            title = { Text("Add Link") },
            text = {
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleTabContent(viewModel: AddEditTaskViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    val dueDate = viewModel.dueDate
    val hasDate = dueDate != null

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // ---- Due Date section ----
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Due Date")

            val today = todayMillis()
            val tomorrow = tomorrowMillis()
            val nextWeek = weekFromNowMillis()
            val matchesShortcut = dueDate == today || dueDate == tomorrow || dueDate == nextWeek

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleChip(
                    label = "Today",
                    selected = dueDate == today,
                    onClick = { viewModel.onDueDateChange(today) }
                )
                ScheduleChip(
                    label = "Tomorrow",
                    selected = dueDate == tomorrow,
                    onClick = { viewModel.onDueDateChange(tomorrow) }
                )
                ScheduleChip(
                    label = "Next Week",
                    selected = dueDate == nextWeek,
                    onClick = { viewModel.onDueDateChange(nextWeek) }
                )
                ScheduleChip(
                    label = "None",
                    selected = dueDate == null,
                    onClick = { viewModel.onDueDateChange(null) }
                )
                if (dueDate != null && !matchesShortcut) {
                    FilterChip(
                        selected = true,
                        onClick = { showDatePicker = true },
                        label = { Text(formatShortDate(dueDate)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear date",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.onDueDateChange(null) }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            TextButton(
                onClick = { showDatePicker = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick Date...")
            }

            if (dueDate != null) {
                Text(
                    text = "\uD83D\uDCC5 ${formatFullDate(dueDate)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ---- Time section (visible when date set) ----
        AnimatedVisibility(
            visible = hasDate,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Time")
                if (viewModel.dueTime == null) {
                    TextButton(
                        onClick = { showTimePicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("\uD83D\uDD50 Add Time")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showTimePicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "\uD83D\uDD50 ${formatTime(viewModel.dueTime!!)}",
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = { viewModel.onDueTimeChange(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear time",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ---- Duration section ----
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Estimated Duration")
            val duration = viewModel.estimatedDuration
            val presets = listOf(
                "15m" to 15,
                "30m" to 30,
                "1h" to 60,
                "1.5h" to 90,
                "2h" to 120,
                "3h" to 180
            )
            val matchesPreset = duration != null && presets.any { it.second == duration }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, minutes) ->
                    ScheduleChip(
                        label = label,
                        selected = duration == minutes,
                        onClick = { viewModel.onEstimatedDurationChange(minutes) }
                    )
                }
                ScheduleChip(
                    label = "Custom",
                    selected = duration != null && !matchesPreset,
                    onClick = { showCustomDurationDialog = true }
                )
            }

            if (duration != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⏱ ${formatDurationMinutes(duration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { viewModel.onEstimatedDurationChange(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear duration",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---- Recurrence section ----
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Repeat")
            val rule = viewModel.recurrenceRule
            if (rule == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Does Not Repeat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showRecurrenceDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Set Recurrence...")
                    }
                }
            } else {
                Text(
                    text = formatRecurrenceSummary(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showRecurrenceDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = { viewModel.onRecurrenceRuleChange(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // ---- Reminder section (visible when date set) ----
        AnimatedVisibility(
            visible = hasDate,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Reminder")
                if (viewModel.reminderOffset == null) {
                    TextButton(
                        onClick = { showReminderDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("\uD83D\uDD14 Add Reminder")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showReminderDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "\uD83D\uDD14 ${reminderOffsetTitleCase(viewModel.reminderOffset)}",
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = { viewModel.onReminderOffsetChange(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear reminder",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = viewModel.dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDueDateChange(state.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    // Reminder picker dialog
    if (showReminderDialog) {
        ReminderPickerDialog(
            currentOffset = viewModel.reminderOffset,
            onSelect = { offset ->
                viewModel.onReminderOffsetChange(offset)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    // Recurrence dialog (wraps the existing RecurrenceSelector dialog internals)
    if (showRecurrenceDialog) {
        RecurrenceDialog(
            initialRule = viewModel.recurrenceRule,
            onDismiss = { showRecurrenceDialog = false },
            onConfirm = { rule ->
                viewModel.onRecurrenceRuleChange(rule)
                showRecurrenceDialog = false
            }
        )
    }

    // Custom duration dialog
    if (showCustomDurationDialog) {
        CustomDurationDialog(
            initialMinutes = viewModel.estimatedDuration,
            onConfirm = { minutes ->
                viewModel.onEstimatedDurationChange(minutes)
                showCustomDurationDialog = false
            },
            onDismiss = { showCustomDurationDialog = false }
        )
    }

    // Time picker dialog
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            viewModel.dueTime?.let { timeInMillis = it }
        }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val picked = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, state.hour)
                    set(Calendar.MINUTE, state.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.onDueTimeChange(picked.timeInMillis)
                showTimePicker = false
            }
        ) {
            TimePicker(state = state)
        }
    }
}

/**
 * Quick-select chip used across the Schedule tab. Renders as a filled accent
 * chip when selected, outlined otherwise (FilterChip's default unselected
 * state).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun CustomDurationDialog(
    initialMinutes: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Duration") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = text.toIntOrNull()?.takeIf { it > 0 }
                onConfirm(minutes)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatShortDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))

private fun formatFullDate(epochMillis: Long): String =
    SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))

private fun formatDurationMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes ${if (minutes == 1) "Minute" else "Minutes"}"
        minutes == 0 -> "$hours ${if (hours == 1) "Hour" else "Hours"}"
        else -> {
            val hourLabel = if (hours == 1) "Hour" else "Hours"
            val minuteLabel = if (minutes == 1) "Minute" else "Minutes"
            "$hours $hourLabel $minutes $minuteLabel"
        }
    }
}

private fun reminderOffsetTitleCase(offset: Long?): String = when (offset) {
    null -> "No Reminder"
    0L -> "At Due Time"
    900_000L -> "15 Minutes Before Due"
    1_800_000L -> "30 Minutes Before Due"
    3_600_000L -> "1 Hour Before Due"
    86_400_000L -> "1 Day Before Due"
    else -> "${offset / 60_000} Minutes Before Due"
}

private fun formatRecurrenceSummary(rule: RecurrenceRule): String {
    val interval = rule.interval.coerceAtLeast(1)
    val base = when (rule.type) {
        RecurrenceType.DAILY -> if (interval == 1) "Every Day" else "Every $interval Days"
        RecurrenceType.WEEKLY -> {
            val prefix = if (interval == 1) "Every Week" else "Every $interval Weeks"
            val days = rule.daysOfWeek?.takeIf { it.isNotEmpty() }?.let { list ->
                val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                list.sorted().joinToString(", ") { names.getOrElse(it - 1) { "" } }
            }
            if (days != null) "$prefix on $days" else prefix
        }
        RecurrenceType.MONTHLY -> {
            val prefix = if (interval == 1) "Every Month" else "Every $interval Months"
            rule.dayOfMonth?.let { "$prefix on Day $it" } ?: prefix
        }
        RecurrenceType.YEARLY -> if (interval == 1) "Every Year" else "Every $interval Years"
        RecurrenceType.CUSTOM -> "Custom"
    }
    return base
}

@Composable
private fun OrganizeTabContent(viewModel: AddEditTaskViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()

    // Project
    SectionLabel("Project")
    ProjectDropdown(
        selectedProjectId = viewModel.projectId,
        projects = projects,
        onSelect = viewModel::onProjectIdChange
    )

    // Tags
    SectionLabel("Tags")
    TagSelector(
        availableTags = allTags,
        selectedTagIds = viewModel.selectedTagIds,
        onSelectionChanged = viewModel::onSelectedTagIdsChange
    )
}
