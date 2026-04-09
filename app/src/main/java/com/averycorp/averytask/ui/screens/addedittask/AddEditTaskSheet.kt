package com.averycorp.averytask.ui.screens.addedittask

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.averytask.ui.components.RecurrenceSelector
import com.averycorp.averytask.ui.components.TagSelector
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.launch
import java.util.Calendar

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

    var showAddLinkDialog by remember { mutableStateOf(false) }
    var attachmentsRevealed by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAddImageAttachment(context, it) }
    }

    // --- Description ---
    Column(modifier = Modifier.animateContentSize()) {
        Text(
            text = "Description",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = viewModel.description,
            onValueChange = viewModel::onDescriptionChange,
            placeholder = { Text("Add Description...") },
            minLines = 2,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // --- Notes (tinted background, 🔒 private marker) ---
    Column(modifier = Modifier.animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "\uD83D\uDD12",
                style = MaterialTheme.typography.labelSmall
            )
        }
        OutlinedTextField(
            value = viewModel.notes,
            onValueChange = viewModel::onNotesChange,
            placeholder = { Text("Private Notes...") },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    // --- Subtasks ---
    // Subtasks are kept in local composable state for now: the AddEdit
    // ViewModel doesn't yet expose subtask flows, and wiring persistence is
    // intentionally out of scope for this UI polish pass.
    SubtasksInlineSection()

    // Keep the attachments section visible for the rest of the session once
    // it first becomes non-empty, so deleting every attachment mid-edit
    // doesn't surprise the user by collapsing it back to a button.
    LaunchedEffect(attachments.isNotEmpty()) {
        if (attachments.isNotEmpty()) attachmentsRevealed = true
    }

    // --- Attachments (edit mode only; hidden when empty until revealed) ---
    if (viewModel.isEditMode) {
        val hasAttachments = attachments.isNotEmpty()
        val showSection = hasAttachments || attachmentsRevealed
        if (showSection) {
            Column(modifier = Modifier.animateContentSize()) {
                SectionLabel(
                    if (hasAttachments) "Attachments (${attachments.size})" else "Attachments"
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (hasAttachments) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        attachments.forEach { attachment ->
                            AttachmentRow(
                                attachment = attachment,
                                onDelete = { viewModel.onDeleteAttachment(context, attachment) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Image")
                    }
                    OutlinedButton(onClick = { showAddLinkDialog = true }) {
                        Icon(
                            Icons.Default.AddLink,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Link")
                    }
                }
            }
        } else {
            TextButton(onClick = { attachmentsRevealed = true }) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Attachment")
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

/**
 * Inline subtasks section for the Details tab.
 *
 * Uses local composable state for now because [AddEditTaskViewModel] doesn't
 * yet expose subtask flows. The UI is fully polished — checklist, header with
 * progress count, drag affordance, inline add with rapid-entry focus — so
 * wiring it to the ViewModel in a follow-up is a purely mechanical change.
 */
@Composable
private fun SubtasksInlineSection() {
    val subtasks = remember { mutableStateListOf<LocalSubtask>() }
    var nextId by remember { mutableStateOf(1L) }
    var newText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val sorted = subtasks.sortedBy { it.isCompleted }
    val completed = subtasks.count { it.isCompleted }
    val total = subtasks.size

    val submit = {
        val trimmed = newText.trim()
        if (trimmed.isNotEmpty()) {
            subtasks.add(LocalSubtask(id = nextId, title = trimmed, isCompleted = false))
            nextId += 1
            newText = ""
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        SectionLabel(
            if (total > 0) "Subtasks ($completed/$total)" else "Subtasks"
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (total == 0) {
            Text(
                text = "Add Subtasks To Break This Task Down",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sorted.forEach { subtask ->
                    LocalSubtaskRow(
                        subtask = subtask,
                        onToggle = {
                            val idx = subtasks.indexOfFirst { it.id == subtask.id }
                            if (idx != -1) {
                                subtasks[idx] =
                                    subtasks[idx].copy(isCompleted = !subtasks[idx].isCompleted)
                            }
                        },
                        onDelete = {
                            subtasks.removeAll { it.id == subtask.id }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        OutlinedTextField(
            value = newText,
            onValueChange = { newText = it },
            placeholder = { Text("Add Subtask...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { submit() }),
            trailingIcon = {
                IconButton(onClick = submit) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Subtask",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

private data class LocalSubtask(
    val id: Long,
    val title: String,
    val isCompleted: Boolean
)

@Composable
private fun LocalSubtaskRow(
    subtask: LocalSubtask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null,
            color = if (subtask.isCompleted)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove Subtask",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleTabContent(viewModel: AddEditTaskViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    // Due date
    SectionLabel("Due Date")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateQuickChip("Today", viewModel.dueDate, todayMillis()) {
            viewModel.onDueDateChange(todayMillis())
        }
        DateQuickChip("Tomorrow", viewModel.dueDate, tomorrowMillis()) {
            viewModel.onDueDateChange(tomorrowMillis())
        }
        DateQuickChip("+1 Week", viewModel.dueDate, weekFromNowMillis()) {
            viewModel.onDueDateChange(weekFromNowMillis())
        }
        FilterChip(
            selected = false,
            onClick = { showDatePicker = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick Date")
                }
            }
        )
        if (viewModel.dueDate != null) {
            FilterChip(
                selected = false,
                onClick = { viewModel.onDueDateChange(null) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            )
        }
    }
    if (viewModel.dueDate != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Due: ${formatDateSmart(viewModel.dueDate!!)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { viewModel.onDueDateChange(null) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear date",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Due time
    SectionLabel("Due Time")
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showTimePicker = true }) {
            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = viewModel.dueTime?.let { formatTime(it) } ?: "No Time"
            )
        }
        if (viewModel.dueTime != null) {
            IconButton(onClick = { viewModel.onDueTimeChange(null) }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear time", modifier = Modifier.size(18.dp))
            }
        }
    }

    // Reminder
    SectionLabel("Reminder")
    val hasDate = viewModel.dueDate != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDate) { showReminderDialog = true }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (viewModel.reminderOffset != null)
                Icons.Default.Notifications
            else
                Icons.Default.NotificationsNone,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (hasDate)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (!hasDate) "Set a due date first"
            else reminderOffsetLabel(viewModel.reminderOffset),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasDate)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    // Recurrence
    SectionLabel("Recurrence")
    RecurrenceSelector(
        currentRule = viewModel.recurrenceRule,
        onRuleChanged = viewModel::onRecurrenceRuleChange
    )

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
