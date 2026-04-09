package com.averycorp.averytask.ui.screens.tasklist

import android.content.ClipData
import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.averytask.data.local.entity.ProjectEntity
import com.averycorp.averytask.data.local.entity.TagEntity
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.domain.model.TaskFilter
import com.averycorp.averytask.ui.components.BatchEditBar
import com.averycorp.averytask.ui.components.BatchMoveToProjectDialog
import com.averycorp.averytask.ui.components.BatchTagsDialog
import com.averycorp.averytask.ui.components.EmptyState
import com.averycorp.averytask.ui.components.FilterPanel
import com.averycorp.averytask.ui.components.MoveToProjectSheet
import com.averycorp.averytask.ui.components.QuickAddBar
import com.averycorp.averytask.ui.components.QuickReschedulePopup
import com.averycorp.averytask.ui.components.SubtaskSection
import com.averycorp.averytask.ui.components.TaskContextMenuSheet
import com.averycorp.averytask.ui.components.computeInitialTagStates
import com.averycorp.averytask.ui.navigation.AveryTaskRoute
import com.averycorp.averytask.ui.screens.addedittask.AddEditTaskSheetHost
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val OverdueRed = Color(0xFFD93025)
private val TodayOrange = Color(0xFFE8872A)

private data class TaskEditorSheetState(
    val taskId: Long? = null,
    val projectId: Long? = null,
    val initialDate: Long? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val groupedTasks by viewModel.groupedTasks.collectAsStateWithLifecycle()
    val tasksByProject by viewModel.tasksByProject.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val taskCountByProject by viewModel.taskCountByProject.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val subtasksMap by viewModel.subtasksMap.collectAsStateWithLifecycle()
    val taskTagsMap by viewModel.taskTagsMap.collectAsStateWithLifecycle()
    val attachmentCountMap by viewModel.attachmentCountMap.collectAsStateWithLifecycle()
    val currentSort by viewModel.currentSort.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val overdueCount by viewModel.overdueCount.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expandedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var focusSubtaskForId by remember { mutableStateOf<Long?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBatchReschedulePopup by remember { mutableStateOf(false) }
    var showBatchTagsDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteContent by remember { mutableStateOf("") }
    var editorSheet by remember { mutableStateOf<TaskEditorSheetState?>(null) }
    var reschedulePopupTask by remember { mutableStateOf<TaskEntity?>(null) }
    // Long-press context menu + move-to-project sheet. The context menu is
    // opened by a long-press on any task card and, when the user picks "Move
    // To Project", hands off to moveToProjectSheetTask which drives the
    // bottom sheet. Confirmation for cascading subtasks is kept separate.
    var contextMenuTask by remember { mutableStateOf<TaskEntity?>(null) }
    var moveToProjectSheetTask by remember { mutableStateOf<TaskEntity?>(null) }
    var cascadeConfirmState by remember {
        mutableStateOf<Pair<TaskEntity, Long?>?>(null)
    }

    // Open the editor sheet when the view model emits an event (e.g. after the
    // user taps "View" on the Task Duplicated snackbar).
    LaunchedEffect(Unit) {
        viewModel.openTaskEditorEvents.collect { taskId ->
            editorSheet = TaskEditorSheetState(taskId = taskId)
        }
    }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromFile(context, it) }
    }

    BackHandler(enabled = isMultiSelectMode) {
        viewModel.onExitMultiSelect()
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasteDialog = false
                pasteContent = ""
            },
            title = { Text("Paste To-Do List") },
            text = {
                OutlinedTextField(
                    value = pasteContent,
                    onValueChange = { pasteContent = it },
                    placeholder = { Text("Paste JSX / markdown list here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 50
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pasteContent.isNotBlank()) {
                            viewModel.importFromText(pasteContent)
                        }
                        showPasteDialog = false
                        pasteContent = ""
                    },
                    enabled = pasteContent.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasteDialog = false
                    pasteContent = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState
        ) {
            FilterPanel(
                currentFilter = currentFilter,
                allTags = allTags,
                allProjects = projects,
                onFilterChanged = { filter ->
                    viewModel.onUpdateFilter(filter)
                    scope.launch {
                        filterSheetState.hide()
                        showFilterSheet = false
                    }
                },
                onClearAll = {
                    viewModel.onClearFilters()
                    scope.launch {
                        filterSheetState.hide()
                        showFilterSheet = false
                    }
                }
            )
        }
    }

    // Bulk tags dialog for multi-select
    if (showBatchTagsDialog) {
        val initialStates = remember(selectedTaskIds, taskTagsMap) {
            computeInitialTagStates(selectedTaskIds, taskTagsMap)
        }
        BatchTagsDialog(
            allTags = allTags,
            initialStates = initialStates,
            onDismiss = { showBatchTagsDialog = false },
            onConfirm = { addIds, removeIds ->
                viewModel.onBulkApplyTags(addIds, removeIds)
                showBatchTagsDialog = false
            }
        )
    }

    // Bulk move-to-project dialog for multi-select
    if (showBatchMoveDialog) {
        val selectedTasks = filteredTasks.filter { it.id in selectedTaskIds }
        // Pre-select the current project id only if every selected task
        // already shares the same project; otherwise default to "None".
        val initialProject = selectedTasks
            .map { it.projectId }
            .distinct()
            .singleOrNull()
        BatchMoveToProjectDialog(
            projects = projects,
            currentProjectId = initialProject,
            onDismiss = { showBatchMoveDialog = false },
            onMove = { projectId ->
                viewModel.onBulkMoveToProject(projectId)
                showBatchMoveDialog = false
            },
            onCreateAndMove = { name ->
                viewModel.onBulkCreateProjectAndMove(name)
                showBatchMoveDialog = false
            }
        )
    }

    // Bulk reschedule popup for multi-select — reuses the same
    // QuickReschedulePopup component as the long-press flow.
    if (showBatchReschedulePopup) {
        QuickReschedulePopup(
            hasDueDate = true,
            onDismiss = { showBatchReschedulePopup = false },
            onReschedule = { newDate ->
                viewModel.onBulkReschedule(newDate)
                showBatchReschedulePopup = false
            },
            onPlanForToday = {
                // Plan-for-today doesn't map to a bulk operation; treat
                // it as rescheduling to today to keep the popup signature.
                val today = com.averycorp.averytask.domain.usecase.DateShortcuts.today(System.currentTimeMillis())
                viewModel.onBulkReschedule(today)
                showBatchReschedulePopup = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedTaskIds.size} Selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onExitMultiSelect() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Multi-Select")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onSelectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tasks",
                                fontWeight = FontWeight.Bold
                            )
                            if (overdueCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OverdueRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$overdueCount",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(AveryTaskRoute.Search.route) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            val filterCount = currentFilter.activeFilterCount()
                            if (filterCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Text("$filterCount")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filters"
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filters"
                                )
                            }
                        }
                        var showViewMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showViewMenu = true }) {
                                Icon(
                                    imageVector = if (viewMode == ViewMode.UPCOMING) Icons.Default.Schedule
                                    else Icons.Default.FormatListBulleted,
                                    contentDescription = "View mode"
                                )
                            }
                            DropdownMenu(
                                expanded = showViewMenu,
                                onDismissRequest = { showViewMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Upcoming") },
                                    onClick = { viewModel.onChangeViewMode(ViewMode.UPCOMING); showViewMenu = false },
                                    trailingIcon = if (viewMode == ViewMode.UPCOMING) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("List") },
                                    onClick = { viewModel.onChangeViewMode(ViewMode.LIST); showViewMenu = false },
                                    trailingIcon = if (viewMode == ViewMode.LIST) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("By Project") },
                                    onClick = { viewModel.onChangeViewMode(ViewMode.BY_PROJECT); showViewMenu = false },
                                    trailingIcon = if (viewMode == ViewMode.BY_PROJECT) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("Week") },
                                    onClick = { showViewMenu = false; navController.navigate(AveryTaskRoute.WeekView.route) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Month") },
                                    onClick = { showViewMenu = false; navController.navigate(AveryTaskRoute.MonthView.route) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Timeline") },
                                    onClick = { showViewMenu = false; navController.navigate(AveryTaskRoute.Timeline.route) }
                                )
                            }
                        }
                        IconButton(onClick = { navController.navigate(AveryTaskRoute.TagManagement.route) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = "Tags"
                            )
                        }
                        IconButton(onClick = { navController.navigate(AveryTaskRoute.Archive.route) }) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Archive"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.SortByAlpha,
                                    contentDescription = "Sort"
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.onChangeSort(option)
                                            showSortMenu = false
                                        },
                                        trailingIcon = if (currentSort == option) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (isMultiSelectMode) {
                BatchEditBar(
                    selectedCount = selectedTaskIds.size,
                    onDeselectAll = { viewModel.onExitMultiSelect() },
                    onComplete = { viewModel.onBulkComplete() },
                    onReschedule = { showBatchReschedulePopup = true },
                    onEditTags = { showBatchTagsDialog = true },
                    onSetPriority = { level -> viewModel.onBulkSetPriority(level) },
                    onMoveToProject = { showBatchMoveDialog = true },
                    onDelete = { viewModel.onBulkDelete() }
                )
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { showPasteDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste To-Do List", modifier = Modifier.size(20.dp))
                    }
                    SmallFloatingActionButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import File", modifier = Modifier.size(20.dp))
                    }
                    FloatingActionButton(
                        onClick = {
                            editorSheet = TaskEditorSheetState(
                                projectId = selectedProjectId
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Task",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ProjectFilterRow(
                projects = projects,
                selectedProjectId = selectedProjectId,
                onSelectProject = viewModel::onSelectProject,
                onManageProjects = { navController.navigate(AveryTaskRoute.ProjectList.route) }
            )

            // Quick add bar
            QuickAddBar()

            // Active filter pills
            if (currentFilter.isActive()) {
                ActiveFilterPills(
                    filter = currentFilter,
                    allTags = allTags,
                    projects = projects,
                    onUpdateFilter = viewModel::onUpdateFilter
                )
            }

            val isCustomSort = currentSort == SortOption.CUSTOM
            val isByProjectView = !isCustomSort && viewMode == ViewMode.BY_PROJECT
            // Custom sort always renders as a flat list (grouping by date
            // doesn't make sense when the user has manually ordered things),
            // regardless of the current view mode toggle.
            val allTasks = when {
                isCustomSort -> filteredTasks
                viewMode == ViewMode.UPCOMING -> groupedTasks.values.flatten()
                isByProjectView -> tasksByProject.values.flatten()
                else -> filteredTasks
            }
            // By-project view always renders its project headers even when
            // every group is empty, so users can still see and drop tasks
            // onto project sections. Skip the empty-state screen in that case.
            if (allTasks.isEmpty() && !isByProjectView) {
                if (currentFilter.isActive()) {
                    EmptyState(
                        icon = Icons.Default.FilterList,
                        title = "No Tasks Match Your Filters",
                        subtitle = "Try adjusting or clearing your filters",
                        modifier = Modifier.weight(1f)
                    )
                } else if (selectedProjectId != null) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No Tasks Match Your Filters",
                        subtitle = "Try selecting a different project",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    EmptyState(
                        icon = Icons.Default.CheckBoxOutlineBlank,
                        title = "No Tasks Yet",
                        subtitle = "Tap + to add your first task",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Local draft ordering for the custom-sort drag-reorder. We
                // mirror the upstream filteredTasks list so drag animations can
                // update the order immediately, then push the committed order
                // back to the ViewModel onDragEnd.
                var draftOrder by remember(filteredTasks, isCustomSort) {
                    mutableStateOf(filteredTasks)
                }
                val lazyListState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val mutable = draftOrder.toMutableList()
                    val fromIdx = mutable.indexOfFirst { it.id == from.key }
                    val toIdx = mutable.indexOfFirst { it.id == to.key }
                    if (fromIdx != -1 && toIdx != -1) {
                        mutable.add(toIdx, mutable.removeAt(fromIdx))
                        draftOrder = mutable
                    }
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    if (isCustomSort) {
                        // Flat reorderable list. Drag handles are shown on the
                        // left; long-press on the whole card also initiates
                        // drag via longPressDraggableHandle.
                        draftOrder.forEach { task ->
                            reorderableTaskItemWithSubtasks(
                                task = task,
                                projects = projects,
                                subtasksMap = subtasksMap,
                                taskTagsMap = taskTagsMap,
                                attachmentCountMap = attachmentCountMap,
                                expandedTaskIds = expandedTaskIds,
                                focusSubtaskForId = focusSubtaskForId,
                                onTaskClick = { id -> editorSheet = TaskEditorSheetState(taskId = id) },
                                viewModel = viewModel,
                                isMultiSelectMode = isMultiSelectMode,
                                selectedTaskIds = selectedTaskIds,
                                onExpandChange = { expandedTaskIds = it },
                                onFocusChange = { focusSubtaskForId = it },
                                reorderState = reorderState,
                                onDragEnd = {
                                    viewModel.onReorderTasks(draftOrder.map { it.id })
                                }
                            )
                        }
                    } else if (isByProjectView) {
                        // Grouped-by-project view. Each project section is a
                        // drop target so users can drag a task card from one
                        // project header to another to reassign it.
                        tasksByProject.forEach { (projectId, tasks) ->
                            val project = projects.find { it.id == projectId }
                            val headerKey = "project_header_${projectId ?: -1L}"
                            item(key = headerKey) {
                                ProjectGroupHeader(
                                    project = project,
                                    taskCount = tasks.size,
                                    onDropTask = { droppedTaskId ->
                                        viewModel.onMoveToProject(droppedTaskId, projectId)
                                    }
                                )
                            }
                            tasks.forEach { task ->
                                draggableTaskItemWithSubtasks(
                                    task = task,
                                    projects = projects,
                                    subtasksMap = subtasksMap,
                                    taskTagsMap = taskTagsMap,
                                    attachmentCountMap = attachmentCountMap,
                                    expandedTaskIds = expandedTaskIds,
                                    focusSubtaskForId = focusSubtaskForId,
                                    onTaskClick = { id -> editorSheet = TaskEditorSheetState(taskId = id) },
                                    onTaskLongPress = { pressed -> contextMenuTask = pressed },
                                    onDropTask = { droppedTaskId ->
                                        viewModel.onMoveToProject(droppedTaskId, projectId)
                                    },
                                    viewModel = viewModel,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedTaskIds = selectedTaskIds,
                                    onExpandChange = { expandedTaskIds = it },
                                    onFocusChange = { focusSubtaskForId = it }
                                )
                            }
                            if (tasks.isEmpty()) {
                                item(key = "empty_project_${projectId ?: -1L}") {
                                    Text(
                                        text = "No Tasks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    } else if (viewMode == ViewMode.UPCOMING) {
                        groupedTasks.forEach { (group, tasks) ->
                            item(key = "header_$group") {
                                GroupHeader(group = group, count = tasks.size)
                            }
                            tasks.forEach { task ->
                                taskItemWithSubtasks(
                                    task = task,
                                    projects = projects,
                                    subtasksMap = subtasksMap,
                                    taskTagsMap = taskTagsMap,
                                    attachmentCountMap = attachmentCountMap,
                                    expandedTaskIds = expandedTaskIds,
                                    focusSubtaskForId = focusSubtaskForId,
                                    onTaskClick = { id -> editorSheet = TaskEditorSheetState(taskId = id) },
                                    onTaskLongPress = { pressed -> contextMenuTask = pressed },
                                    viewModel = viewModel,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedTaskIds = selectedTaskIds,
                                    onExpandChange = { expandedTaskIds = it },
                                    onFocusChange = { focusSubtaskForId = it }
                                )
                            }
                        }
                    } else {
                        filteredTasks.forEach { task ->
                            taskItemWithSubtasks(
                                task = task,
                                projects = projects,
                                subtasksMap = subtasksMap,
                                taskTagsMap = taskTagsMap,
                                attachmentCountMap = attachmentCountMap,
                                expandedTaskIds = expandedTaskIds,
                                focusSubtaskForId = focusSubtaskForId,
                                onTaskClick = { id -> editorSheet = TaskEditorSheetState(taskId = id) },
                                onTaskLongPress = { pressed -> contextMenuTask = pressed },
                                viewModel = viewModel,
                                isMultiSelectMode = isMultiSelectMode,
                                selectedTaskIds = selectedTaskIds,
                                onExpandChange = { expandedTaskIds = it },
                                onFocusChange = { focusSubtaskForId = it }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    editorSheet?.let { state ->
        AddEditTaskSheetHost(
            taskId = state.taskId,
            projectId = state.projectId,
            initialDate = state.initialDate,
            onDismiss = { editorSheet = null },
            onDeleteTask = { id -> viewModel.onDeleteTaskWithUndo(id) }
        )
    }

    reschedulePopupTask?.let { task ->
        QuickReschedulePopup(
            hasDueDate = task.dueDate != null,
            onDismiss = { reschedulePopupTask = null },
            onReschedule = { newDate ->
                viewModel.onRescheduleTask(task.id, newDate)
            },
            onPlanForToday = {
                viewModel.onPlanForToday(task.id)
            }
        )
    }

    contextMenuTask?.let { task ->
        TaskContextMenuSheet(
            taskTitle = task.title,
            onDismiss = { contextMenuTask = null },
            onReschedule = {
                contextMenuTask = null
                reschedulePopupTask = task
            },
            onMoveToProject = {
                contextMenuTask = null
                moveToProjectSheetTask = task
            }
        )
    }

    moveToProjectSheetTask?.let { task ->
        val subtaskCount = subtasksMap[task.id]?.size ?: 0
        MoveToProjectSheet(
            projects = projects,
            taskCountByProject = taskCountByProject,
            currentProjectId = task.projectId,
            onDismiss = { moveToProjectSheetTask = null },
            onMove = { newProjectId ->
                moveToProjectSheetTask = null
                if (subtaskCount > 0) {
                    cascadeConfirmState = task to newProjectId
                } else {
                    viewModel.onMoveToProject(task.id, newProjectId)
                }
            },
            onCreateAndMove = { name ->
                moveToProjectSheetTask = null
                viewModel.onCreateProjectAndMoveTask(task.id, name, cascadeSubtasks = subtaskCount > 0)
            }
        )
    }

    cascadeConfirmState?.let { (task, newProjectId) ->
        AlertDialog(
            onDismissRequest = { cascadeConfirmState = null },
            title = { Text("Move Subtasks Too?") },
            text = {
                Text("'${task.title}' has subtasks. Should they move to the same project?")
            },
            confirmButton = {
                TextButton(onClick = {
                    cascadeConfirmState = null
                    viewModel.onMoveToProject(task.id, newProjectId, cascadeSubtasks = true)
                }) { Text("Yes, Move All") }
            },
            dismissButton = {
                TextButton(onClick = {
                    cascadeConfirmState = null
                    viewModel.onMoveToProject(task.id, newProjectId, cascadeSubtasks = false)
                }) { Text("No, Just This") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.reorderableTaskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    taskTagsMap: Map<Long, List<TagEntity>>,
    attachmentCountMap: Map<Long, Int>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    onTaskClick: (Long) -> Unit,
    viewModel: TaskListViewModel,
    isMultiSelectMode: Boolean,
    selectedTaskIds: Set<Long>,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit,
    reorderState: ReorderableLazyListState,
    onDragEnd: () -> Unit
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    val tags = taskTagsMap[task.id].orEmpty()
    val attachmentCount = attachmentCountMap[task.id] ?: 0
    val project = projects.find { it.id == task.projectId }

    item(key = task.id) {
        ReorderableItem(reorderState, key = task.id) { isDragging ->
            val elevation = if (isDragging) 8.dp else 0.dp
            val scale = if (isDragging) 1.02f else 1f
            val alpha = if (isDragging) 0.85f else 1f

            if (isMultiSelectMode) {
                TaskItem(
                    task = task,
                    project = project,
                    subtasks = subtasks,
                    tags = tags,
                    attachmentCount = attachmentCount,
                    isSelected = task.id in selectedTaskIds,
                    isMultiSelectMode = true,
                    onToggleComplete = { viewModel.onToggleTaskSelection(task.id) },
                    onClick = { viewModel.onToggleTaskSelection(task.id) },
                    onAddSubtaskClick = {}
                )
            } else {
                TaskItem(
                    task = task,
                    project = project,
                    subtasks = subtasks,
                    tags = tags,
                    attachmentCount = attachmentCount,
                    onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                    onClick = { onTaskClick(task.id) },
                    onAddSubtaskClick = {
                        onExpandChange(expandedTaskIds + task.id)
                        onFocusChange(task.id)
                    },
                    onDuplicate = { viewModel.onDuplicateTask(task.id) },
                    showDragHandle = true,
                    dragHandleModifier = Modifier.longPressDraggableHandle(
                        onDragStopped = { onDragEnd() }
                    ),
                    modifier = Modifier
                        .longPressDraggableHandle(
                            onDragStopped = { onDragEnd() }
                        )
                        .shadow(elevation, RoundedCornerShape(12.dp))
                        .scale(scale)
                        .alpha(alpha)
                )
            }
        }
    }
    if (subtasks.isNotEmpty() || expandedTaskIds.contains(task.id)) {
        item(key = "subtasks_${task.id}") {
            SubtaskSection(
                parentTaskId = task.id,
                subtasks = subtasks,
                onToggleComplete = viewModel::onToggleSubtaskComplete,
                onAddSubtask = { title, parentId, priority ->
                    viewModel.onAddSubtask(title, parentId, priority)
                },
                onDeleteSubtask = viewModel::onDeleteSubtaskWithUndo,
                onReorderSubtasks = viewModel::onReorderSubtasks,
                expanded = expandedTaskIds.contains(task.id),
                onToggleExpand = {
                    onExpandChange(
                        if (expandedTaskIds.contains(task.id))
                            expandedTaskIds - task.id
                        else
                            expandedTaskIds + task.id
                    )
                },
                requestFocus = focusSubtaskForId == task.id,
                onFocusHandled = { onFocusChange(null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.taskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    taskTagsMap: Map<Long, List<TagEntity>>,
    attachmentCountMap: Map<Long, Int>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    onTaskClick: (Long) -> Unit,
    onTaskLongPress: (TaskEntity) -> Unit,
    viewModel: TaskListViewModel,
    isMultiSelectMode: Boolean,
    selectedTaskIds: Set<Long>,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    val tags = taskTagsMap[task.id].orEmpty()
    val attachmentCount = attachmentCountMap[task.id] ?: 0
    item(key = task.id) {
        val project = projects.find { it.id == task.projectId }

        if (isMultiSelectMode) {
            TaskItem(
                task = task,
                project = project,
                subtasks = subtasks,
                tags = tags,
                attachmentCount = attachmentCount,
                isSelected = task.id in selectedTaskIds,
                isMultiSelectMode = true,
                onToggleComplete = { viewModel.onToggleTaskSelection(task.id) },
                onClick = { viewModel.onToggleTaskSelection(task.id) },
                onAddSubtaskClick = {}
            )
        } else {
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            viewModel.onCompleteTaskWithUndo(task.id)
                            true
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            viewModel.onDeleteTaskWithUndo(task.id)
                            true
                        }
                        SwipeToDismissBoxValue.Settled -> false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val backgroundColor = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                        SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935)
                        else -> Color.Transparent
                    }
                    val icon = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                        else -> Icons.Default.Check
                    }
                    val alignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                            .padding(horizontal = 20.dp),
                        contentAlignment = alignment
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            ) {
                TaskItem(
                    task = task,
                    project = project,
                    subtasks = subtasks,
                    tags = tags,
                    attachmentCount = attachmentCount,
                    onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                    onClick = { onTaskClick(task.id) },
                    onLongClick = { onTaskLongPress(task) },
                    onAddSubtaskClick = {
                        onExpandChange(expandedTaskIds + task.id)
                        onFocusChange(task.id)
                    },
                    onDuplicate = { viewModel.onDuplicateTask(task.id) }
                )
            }
        }
    }
    if (subtasks.isNotEmpty() || expandedTaskIds.contains(task.id)) {
        item(key = "subtasks_${task.id}") {
            SubtaskSection(
                parentTaskId = task.id,
                subtasks = subtasks,
                onToggleComplete = viewModel::onToggleSubtaskComplete,
                onAddSubtask = { title, parentId, priority ->
                    viewModel.onAddSubtask(title, parentId, priority)
                },
                onDeleteSubtask = viewModel::onDeleteSubtaskWithUndo,
                onReorderSubtasks = viewModel::onReorderSubtasks,
                expanded = expandedTaskIds.contains(task.id),
                onToggleExpand = {
                    onExpandChange(
                        if (expandedTaskIds.contains(task.id))
                            expandedTaskIds - task.id
                        else
                            expandedTaskIds + task.id
                    )
                },
                requestFocus = focusSubtaskForId == task.id,
                onFocusHandled = { onFocusChange(null) }
            )
        }
    }
}

@Composable
private fun GroupHeader(group: String, count: Int) {
    val color = when (group) {
        "Overdue" -> OverdueRed
        "Today" -> TodayOrange
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f)
        )
    }
}

/**
 * Header row for a project section in the By Project view. Shows the
 * project's colored dot + name + task count, and acts as a drop target
 * for the cross-project drag-to-move interaction: any task card dropped
 * onto the header is reassigned to this project (or "No Project" when
 * [project] is null).
 *
 * While a drag is hovering over the header the row scales up slightly
 * and draws an accent border so the drop target is unambiguous. The
 * drag payload is a plain-text ClipData carrying the task id as a long.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectGroupHeader(
    project: ProjectEntity?,
    taskCount: Int,
    onDropTask: (Long) -> Unit
) {
    val accent = if (project != null) {
        try {
            Color(android.graphics.Color.parseColor(project.color))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.primary
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1f,
        label = "projectHeaderScale"
    )

    val target = remember(project?.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { isHovered = true }
            override fun onExited(event: DragAndDropEvent) { isHovered = false }
            override fun onEnded(event: DragAndDropEvent) { isHovered = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipItem = event.toAndroidDragEvent().clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                val droppedId = clipItem?.text?.toString()?.toLongOrNull()
                isHovered = false
                return if (droppedId != null) {
                    onDropTask(droppedId)
                    true
                } else {
                    false
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isHovered) Modifier.border(
                    width = 2.dp,
                    color = accent,
                    shape = RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .background(
                if (isHovered) accent.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = target
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = project?.name ?: "No Project",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$taskCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Variant of [taskItemWithSubtasks] used inside the By Project view. The
 * card is a drag-and-drop source: long-press starts a native drag whose
 * ClipData carries this task's id, and the card itself acts as a drop
 * target so dropping another task on top of it moves that task into this
 * card's project. Long-press still opens the context menu via the normal
 * combinedClickable path — only the actual drag gesture (long-press +
 * move) triggers [dragAndDropSource].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.draggableTaskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    taskTagsMap: Map<Long, List<TagEntity>>,
    attachmentCountMap: Map<Long, Int>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    onTaskClick: (Long) -> Unit,
    onTaskLongPress: (TaskEntity) -> Unit,
    onDropTask: (Long) -> Unit,
    viewModel: TaskListViewModel,
    isMultiSelectMode: Boolean,
    selectedTaskIds: Set<Long>,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    val tags = taskTagsMap[task.id].orEmpty()
    val attachmentCount = attachmentCountMap[task.id] ?: 0
    val project = projects.find { it.id == task.projectId }
    item(key = "proj_task_${task.id}") {
        var isHovered by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isHovered) 1.02f else 1f,
            label = "taskDragScale"
        )

        val dropTarget = remember(task.id, task.projectId) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) { isHovered = true }
                override fun onExited(event: DragAndDropEvent) { isHovered = false }
                override fun onEnded(event: DragAndDropEvent) { isHovered = false }
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val clipItem = event.toAndroidDragEvent().clipData
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                    val droppedId = clipItem?.text?.toString()?.toLongOrNull()
                    isHovered = false
                    if (droppedId == null || droppedId == task.id) return false
                    onDropTask(droppedId)
                    return true
                }
            }
        }

        // Drag is initiated via the explicit drag handle so that the task
        // card's long-press gesture remains free to open the shared
        // context menu. The whole card stays a drop target so users can
        // release a dragged card on top of any task in the destination
        // project — not just on the project header. The drag shadow is
        // a small semi-transparent rounded rect so the user sees that a
        // drag is in progress — native Android handles the actual
        // follow-the-pointer movement on top of that decoration.
        val dragShadowColor = MaterialTheme.colorScheme.primary
        val dragHandleDragModifier = Modifier.dragAndDropSource(
            drawDragDecoration = {
                drawRoundRect(
                    color = dragShadowColor,
                    alpha = 0.5f,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
        ) {
            detectTapGestures(
                onLongPress = {
                    startTransfer(
                        DragAndDropTransferData(
                            clipData = ClipData.newPlainText(
                                "task_id",
                                task.id.toString()
                            )
                        )
                    )
                }
            )
        }

        val dragModifier = Modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = dropTarget
            )
            .shadow(if (isHovered) 6.dp else 0.dp, RoundedCornerShape(12.dp))
            .scale(scale)

        if (isMultiSelectMode) {
            TaskItem(
                task = task,
                project = project,
                subtasks = subtasks,
                tags = tags,
                attachmentCount = attachmentCount,
                isSelected = task.id in selectedTaskIds,
                isMultiSelectMode = true,
                onToggleComplete = { viewModel.onToggleTaskSelection(task.id) },
                onClick = { viewModel.onToggleTaskSelection(task.id) },
                onAddSubtaskClick = {},
                showDragHandle = true,
                dragHandleModifier = dragHandleDragModifier,
                modifier = dragModifier
            )
        } else {
            TaskItem(
                task = task,
                project = project,
                subtasks = subtasks,
                tags = tags,
                attachmentCount = attachmentCount,
                onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                onClick = { onTaskClick(task.id) },
                onLongClick = { onTaskLongPress(task) },
                onAddSubtaskClick = {
                    onExpandChange(expandedTaskIds + task.id)
                    onFocusChange(task.id)
                },
                onDuplicate = { viewModel.onDuplicateTask(task.id) },
                showDragHandle = true,
                dragHandleModifier = dragHandleDragModifier,
                modifier = dragModifier
            )
        }
    }
    if (subtasks.isNotEmpty() || expandedTaskIds.contains(task.id)) {
        item(key = "proj_subtasks_${task.id}") {
            SubtaskSection(
                parentTaskId = task.id,
                subtasks = subtasks,
                onToggleComplete = viewModel::onToggleSubtaskComplete,
                onAddSubtask = { title, parentId, priority ->
                    viewModel.onAddSubtask(title, parentId, priority)
                },
                onDeleteSubtask = viewModel::onDeleteSubtaskWithUndo,
                onReorderSubtasks = viewModel::onReorderSubtasks,
                expanded = expandedTaskIds.contains(task.id),
                onToggleExpand = {
                    onExpandChange(
                        if (expandedTaskIds.contains(task.id))
                            expandedTaskIds - task.id
                        else
                            expandedTaskIds + task.id
                    )
                },
                requestFocus = focusSubtaskForId == task.id,
                onFocusHandled = { onFocusChange(null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectFilterRow(
    projects: List<ProjectEntity>,
    selectedProjectId: Long?,
    onSelectProject: (Long?) -> Unit,
    onManageProjects: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        item {
            FilterChip(
                selected = selectedProjectId == null,
                onClick = { onSelectProject(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        items(projects, key = { it.id }) { project ->
            val projectColor = try {
                Color(android.graphics.Color.parseColor(project.color))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primary
            }
            FilterChip(
                selected = selectedProjectId == project.id,
                onClick = { onSelectProject(project.id) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(projectColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(project.name)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = projectColor.copy(alpha = 0.15f),
                    selectedLabelColor = projectColor
                )
            )
        }
        item {
            AssistChip(
                onClick = onManageProjects,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FolderCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage")
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskItem(
    task: TaskEntity,
    project: ProjectEntity?,
    subtasks: List<TaskEntity>,
    tags: List<TagEntity> = emptyList(),
    attachmentCount: Int = 0,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onAddSubtaskClick: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val isOverdue = isTaskOverdue(task)
    val borderColor = if (isOverdue) OverdueRed else Color.Transparent

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isOverdue -> OverdueRed.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isOverdue) {
                        drawLine(
                            color = borderColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                }
                .padding(start = if (isOverdue) 6.dp else 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag To Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = dragHandleModifier.size(24.dp)
                )
            }
            Checkbox(
                checked = if (isMultiSelectMode) isSelected else task.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    task.dueDate?.let { millis ->
                        val label = formatDueDate(millis)
                        Text(
                            text = label.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = label.color
                        )
                    }

                    if (task.reminderOffset != null) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Reminder set",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (task.recurrenceRule != null) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Recurring task",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!task.notes.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Has notes",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (attachmentCount > 0) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Has attachments",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (subtasks.isNotEmpty()) {
                        val completed = subtasks.count { it.isCompleted }
                        Text(
                            text = "$completed/${subtasks.size} subtasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (project != null) {
                        ProjectChip(project)
                    }
                }

                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val visibleTags = tags.take(3)
                        visibleTags.forEach { tag ->
                            TagChip(tag)
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onAddSubtaskClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.AddTask,
                    contentDescription = "Add subtask",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (onDuplicate != null) {
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More Actions",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCCB  Duplicate") },
                            onClick = {
                                showOverflowMenu = false
                                onDuplicate()
                            }
                        )
                    }
                }
            }

            PriorityDot(task.priority)
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

private fun isTaskOverdue(task: TaskEntity): Boolean {
    if (task.isCompleted || task.dueDate == null) return false
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return task.dueDate < startOfToday
}

@Composable
private fun PriorityDot(priority: Int) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(LocalPriorityColors.current.forLevel(priority))
    )
}

@Composable
private fun TagChip(tag: TagEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(tag.color))
    } catch (_: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(chipColor)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ProjectChip(project: ProjectEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(project.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class DueDateLabel(val text: String, val color: Color)

@Composable
private fun formatDueDate(epochMillis: Long): DueDateLabel {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfTomorrow = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfDayAfter = cal.timeInMillis

    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    return when {
        epochMillis < startOfToday -> {
            val formatted = dateFmt.format(Date(epochMillis))
            DueDateLabel("Overdue \u00B7 $formatted", OverdueRed)
        }
        epochMillis < startOfTomorrow -> DueDateLabel("Today", TodayOrange)
        epochMillis < startOfDayAfter -> DueDateLabel("Tomorrow", normal)
        else -> DueDateLabel(dateFmt.format(Date(epochMillis)), normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterPills(
    filter: TaskFilter,
    allTags: List<TagEntity>,
    projects: List<ProjectEntity>,
    onUpdateFilter: (TaskFilter) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (filter.selectedTagIds.isNotEmpty()) {
            val tagNames = allTags.filter { it.id in filter.selectedTagIds }.joinToString(", ") { it.name }
            val modeLabel = if (filter.tagFilterMode == com.averycorp.averytask.domain.model.TagFilterMode.ALL) "ALL" else "ANY"
            RemovableFilterChip(
                label = "Tags ($modeLabel): $tagNames",
                onRemove = { onUpdateFilter(filter.copy(selectedTagIds = emptyList())) }
            )
        }

        if (filter.selectedPriorities.isNotEmpty()) {
            val labels = filter.selectedPriorities.sorted().joinToString(", ") { p ->
                when (p) { 0 -> "None"; 1 -> "Low"; 2 -> "Med"; 3 -> "High"; 4 -> "Urgent"; else -> "$p" }
            }
            RemovableFilterChip(
                label = "Priority: $labels",
                onRemove = { onUpdateFilter(filter.copy(selectedPriorities = emptyList())) }
            )
        }

        if (filter.selectedProjectIds.isNotEmpty()) {
            val projNames = projects.filter { it.id in filter.selectedProjectIds }.joinToString(", ") { it.name }
            RemovableFilterChip(
                label = "Project: $projNames",
                onRemove = { onUpdateFilter(filter.copy(selectedProjectIds = emptyList())) }
            )
        }

        if (filter.dateRange != null) {
            val rangeLabel = when {
                filter.dateRange.start == null && filter.dateRange.end == null -> "No Date"
                else -> "Date range"
            }
            RemovableFilterChip(
                label = rangeLabel,
                onRemove = { onUpdateFilter(filter.copy(dateRange = null)) }
            )
        }

        if (filter.showCompleted) {
            RemovableFilterChip(
                label = "Completed",
                onRemove = { onUpdateFilter(filter.copy(showCompleted = false)) }
            )
        }

        if (filter.showArchived) {
            RemovableFilterChip(
                label = "Archived",
                onRemove = { onUpdateFilter(filter.copy(showArchived = false)) }
            )
        }

        if (filter.searchQuery.isNotBlank()) {
            RemovableFilterChip(
                label = "\"${filter.searchQuery}\"",
                onRemove = { onUpdateFilter(filter.copy(searchQuery = "")) }
            )
        }
    }
}

@Composable
private fun RemovableFilterChip(
    label: String,
    onRemove: () -> Unit
) {
    AssistChip(
        onClick = onRemove,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove filter",
                modifier = Modifier.size(14.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

