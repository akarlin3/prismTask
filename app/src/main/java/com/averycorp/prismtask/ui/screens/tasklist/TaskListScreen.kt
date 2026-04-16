package com.averycorp.prismtask.ui.screens.tasklist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.ui.components.BatchEditBar
import com.averycorp.prismtask.ui.components.BatchMoveToProjectDialog
import com.averycorp.prismtask.ui.components.BatchTagsDialog
import com.averycorp.prismtask.ui.components.CircularCheckbox
import com.averycorp.prismtask.ui.components.FilterPanel
import com.averycorp.prismtask.ui.components.MoveToProjectSheet
import com.averycorp.prismtask.ui.components.QuickAddBar
import com.averycorp.prismtask.ui.components.QuickReschedulePopup
import com.averycorp.prismtask.ui.components.RichEmptyState
import com.averycorp.prismtask.ui.components.TaskListSkeleton
import com.averycorp.prismtask.ui.components.computeInitialTagStates
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.addedittask.AddEditTaskSheetHost
import com.averycorp.prismtask.ui.screens.tasklist.components.ActiveFilterPills
import com.averycorp.prismtask.ui.screens.tasklist.components.DuplicateDialogState
import com.averycorp.prismtask.ui.screens.tasklist.components.GroupHeader
import com.averycorp.prismtask.ui.screens.tasklist.components.ProjectFilterRow
import com.averycorp.prismtask.ui.screens.tasklist.components.ProjectGroupHeader
import com.averycorp.prismtask.ui.screens.tasklist.components.draggableTaskItemWithSubtasks
import com.averycorp.prismtask.ui.screens.tasklist.components.reorderableTaskItemWithSubtasks
import com.averycorp.prismtask.ui.screens.tasklist.components.taskItemWithSubtasks
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.LocalPrismTheme
import com.averycorp.prismtask.ui.theme.prismDisplayFont
import kotlinx.coroutines.launch
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TodayOrange = Color(0xFFE8872A)

private data class TaskEditorSheetState(
    val taskId: Long? = null,
    val projectId: Long? = null,
    val initialDate: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiTier by viewModel.uiTier.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val swipePrefs by viewModel.swipePrefs.collectAsStateWithLifecycle()
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
    // Move-to-project sheet, triggered from the 3-dot overflow menu on each
    // task card. Confirmation for cascading subtasks is kept separate.
    var moveToProjectSheetTask by remember { mutableStateOf<TaskEntity?>(null) }
    var cascadeConfirmState by remember {
        mutableStateOf<Pair<TaskEntity, Long?>?>(null)
    }
    var duplicateDialogState by remember { mutableStateOf<DuplicateDialogState?>(null) }

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

    // Duplicate confirmation dialog — shown from the task card context menu.
    duplicateDialogState?.let { dupState ->
        var copyDueDate by remember { mutableStateOf(true) }
        var includeSubtasks by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { duplicateDialogState = null },
            title = { Text("Duplicate Task") },
            text = {
                Column {
                    Text(
                        text = "A copy will be created with \"Copy of \" prefixed to the title.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (dupState.dueDate != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { copyDueDate = !copyDueDate }
                                .padding(vertical = 4.dp)
                        ) {
                            CircularCheckbox(
                                checked = copyDueDate,
                                onCheckedChange = { copyDueDate = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Copy Due Date (${
                                    SimpleDateFormat("MMM d", Locale.getDefault())
                                        .format(Date(dupState.dueDate))
                                })"
                            )
                        }
                    }
                    if (dupState.subtaskCount > 0) {
                        Spacer(modifier = Modifier.height(if (dupState.dueDate != null) 4.dp else 12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { includeSubtasks = !includeSubtasks }
                                .padding(vertical = 4.dp)
                        ) {
                            CircularCheckbox(
                                checked = includeSubtasks,
                                onCheckedChange = { includeSubtasks = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Include Subtasks (${dupState.subtaskCount})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val state = dupState
                        duplicateDialogState = null
                        viewModel.onDuplicateTask(
                            taskId = state.taskId,
                            includeSubtasks = includeSubtasks,
                            copyDueDate = state.dueDate != null && copyDueDate
                        )
                    }
                ) { Text("Duplicate") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateDialogState = null }) { Text("Cancel") }
            }
        )
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
    // QuickReschedulePopup component as the single-task overflow menu flow.
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
                val today = com.averycorp.prismtask.domain.usecase.DateShortcuts
                    .today(System.currentTimeMillis())
                viewModel.onBulkReschedule(today)
                showBatchReschedulePopup = false
            }
        )
    }

    val prismColors = LocalPrismColors.current
    val prismTheme = LocalPrismTheme.current
    val displayFont = prismDisplayFont(prismTheme)
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        containerColor = prismColors.background,
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedTaskIds.size} Selected",
                            fontFamily = displayFont,
                            fontWeight = FontWeight.Bold,
                            color = prismColors.onBackground
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
                        containerColor = prismColors.primary.copy(alpha = 0.12f),
                        titleContentColor = prismColors.onBackground,
                        navigationIconContentColor = prismColors.onBackground,
                        actionIconContentColor = prismColors.onBackground
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "Tasks",
                            fontFamily = displayFont,
                            fontWeight = FontWeight.Bold,
                            color = prismColors.onBackground
                        )
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(PrismTaskRoute.Search.route) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                        // Filter button — STANDARD+ only
                        if (uiTier.isAtLeast(com.averycorp.prismtask.domain.model.UiComplexityTier.STANDARD)) {
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
                        }
                        var showViewMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showViewMenu = true }) {
                                Icon(
                                    imageVector = if (viewMode == ViewMode.UPCOMING) {
                                        Icons.Default.Schedule
                                    } else {
                                        Icons.Default.FormatListBulleted
                                    },
                                    contentDescription = "View mode"
                                )
                            }
                            DropdownMenu(
                                expanded = showViewMenu,
                                onDismissRequest = { showViewMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Upcoming") },
                                    onClick = {
                                        viewModel.onChangeViewMode(ViewMode.UPCOMING)
                                        showViewMenu = false
                                    },
                                    trailingIcon = if (viewMode ==
                                        ViewMode.UPCOMING
                                    ) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                    } else {
                                        null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("List") },
                                    onClick = {
                                        viewModel.onChangeViewMode(ViewMode.LIST)
                                        showViewMenu = false
                                    },
                                    trailingIcon = if (viewMode ==
                                        ViewMode.LIST
                                    ) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                    } else {
                                        null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("By Project") },
                                    onClick = {
                                        viewModel.onChangeViewMode(ViewMode.BY_PROJECT)
                                        showViewMenu = false
                                    },
                                    trailingIcon = if (viewMode ==
                                        ViewMode.BY_PROJECT
                                    ) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                    } else {
                                        null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Week") },
                                    onClick = {
                                        showViewMenu = false
                                        navController.navigate(PrismTaskRoute.WeekView.route)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Month") },
                                    onClick = {
                                        showViewMenu = false
                                        navController.navigate(PrismTaskRoute.MonthView.route)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Timeline") },
                                    onClick = {
                                        showViewMenu = false
                                        navController.navigate(PrismTaskRoute.Timeline.route)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Eisenhower Matrix") },
                                    onClick = {
                                        showViewMenu = false
                                        navController.navigate(PrismTaskRoute.EisenhowerMatrix.route)
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { navController.navigate(PrismTaskRoute.TagManagement.route) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = "Tags"
                            )
                        }
                        IconButton(onClick = { navController.navigate(PrismTaskRoute.Archive.route) }) {
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
                                val basicSortOptions =
                                    setOf(SortOption.DUE_DATE, SortOption.PRIORITY, SortOption.CREATED)
                                val visibleSortOptions = SortOption.entries.filter { option ->
                                    when {
                                        uiTier == com.averycorp.prismtask.domain.model.UiComplexityTier.BASIC ->
                                            option in basicSortOptions
                                        else -> true
                                    }
                                }
                                visibleSortOptions.forEach { option ->
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
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = prismColors.background,
                        titleContentColor = prismColors.onBackground,
                        actionIconContentColor = prismColors.onBackground
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
            if (isLoading) {
                TaskListSkeleton(count = 8)
                return@Column
            }
            ProjectFilterRow(
                projects = projects,
                selectedProjectId = selectedProjectId,
                onSelectProject = viewModel::onSelectProject,
                onManageProjects = { navController.navigate(PrismTaskRoute.ProjectList.route) }
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
                if (currentFilter.isActive() || selectedProjectId != null) {
                    RichEmptyState(
                        icon = "\uD83D\uDD0D",
                        title = "No Matching Tasks",
                        description = "Try adjusting your filters or search terms.",
                        actionLabel = "Clear Filters",
                        onAction = { viewModel.onClearFilters() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    RichEmptyState(
                        icon = "\u2728",
                        title = "Clean Slate",
                        description = "Add something when you're ready.",
                        actionLabel = "Create Task",
                        onAction = {
                            editorSheet = TaskEditorSheetState()
                        },
                        secondaryActionLabel = "Use a Template",
                        onSecondaryAction = {
                            navController.navigate(PrismTaskRoute.TemplateList.route)
                        },
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
                                onReschedule = { pressed -> reschedulePopupTask = pressed },
                                onMoveToProject = { pressed -> moveToProjectSheetTask = pressed },
                                viewModel = viewModel,
                                isMultiSelectMode = isMultiSelectMode,
                                selectedTaskIds = selectedTaskIds,
                                onExpandChange = { expandedTaskIds = it },
                                onFocusChange = { focusSubtaskForId = it },
                                reorderState = reorderState,
                                onDragEnd = {
                                    viewModel.onReorderTasks(draftOrder.map { it.id })
                                },
                                onDuplicate = { duplicateDialogState = it }
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
                                    onReschedule = { pressed -> reschedulePopupTask = pressed },
                                    onMoveToProject = { pressed -> moveToProjectSheetTask = pressed },
                                    onDropTask = { droppedTaskId ->
                                        viewModel.onMoveToProject(droppedTaskId, projectId)
                                    },
                                    viewModel = viewModel,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedTaskIds = selectedTaskIds,
                                    onExpandChange = { expandedTaskIds = it },
                                    onFocusChange = { focusSubtaskForId = it },
                                    onDuplicate = { duplicateDialogState = it }
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
                                    onReschedule = { pressed -> reschedulePopupTask = pressed },
                                    onMoveToProject = { pressed -> moveToProjectSheetTask = pressed },
                                    viewModel = viewModel,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedTaskIds = selectedTaskIds,
                                    onExpandChange = { expandedTaskIds = it },
                                    onFocusChange = { focusSubtaskForId = it },
                                    onDuplicate = { duplicateDialogState = it },
                                    swipePrefs = swipePrefs
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
                                onReschedule = { pressed -> reschedulePopupTask = pressed },
                                onMoveToProject = { pressed -> moveToProjectSheetTask = pressed },
                                viewModel = viewModel,
                                isMultiSelectMode = isMultiSelectMode,
                                selectedTaskIds = selectedTaskIds,
                                onExpandChange = { expandedTaskIds = it },
                                onFocusChange = { focusSubtaskForId = it },
                                onDuplicate = { duplicateDialogState = it },
                                swipePrefs = swipePrefs
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
            onDeleteTask = { id -> viewModel.onDeleteTaskWithUndo(id) },
            onManageTemplates = {
                editorSheet = null
                navController.navigate(PrismTaskRoute.TemplateList.route)
            }
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
