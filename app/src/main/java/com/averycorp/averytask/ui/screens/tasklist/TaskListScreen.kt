package com.averycorp.averytask.ui.screens.tasklist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.averycorp.averytask.ui.components.EmptyState
import com.averycorp.averytask.ui.components.FilterPanel
import com.averycorp.averytask.ui.components.QuickAddBar
import com.averycorp.averytask.ui.components.SubtaskSection
import com.averycorp.averytask.ui.navigation.AveryTaskRoute
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val OverdueRed = Color(0xFFD93025)
private val TodayOrange = Color(0xFFE8872A)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val groupedTasks by viewModel.groupedTasks.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
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
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteContent by remember { mutableStateOf("") }
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

    // Priority picker dialog for multi-select
    if (showPriorityDialog) {
        var selectedPriority by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onBulkSetPriority(selectedPriority)
                    showPriorityDialog = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showPriorityDialog = false }) { Text("Cancel") }
            },
            title = { Text("Set Priority") },
            text = {
                Column {
                    listOf(0 to "None", 1 to "Low", 2 to "Medium", 3 to "High", 4 to "Urgent").forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPriority = value }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPriority == value,
                                onClick = { selectedPriority = value }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        topBar = {
            if (isMultiSelectMode) {
                var showMultiSelectMenu by remember { mutableStateOf(false) }
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedTaskIds.size} selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onExitMultiSelect() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit multi-select")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onSelectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = { viewModel.onBulkComplete() }) {
                            Icon(Icons.Default.Check, contentDescription = "Complete selected")
                        }
                        IconButton(onClick = { viewModel.onBulkDelete() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                        Box {
                            IconButton(onClick = { showMultiSelectMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMultiSelectMenu,
                                onDismissRequest = { showMultiSelectMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Set Priority") },
                                    onClick = {
                                        showMultiSelectMenu = false
                                        showPriorityDialog = true
                                    }
                                )
                            }
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
                        onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute()) },
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

            val allTasks = if (viewMode == ViewMode.UPCOMING)
                groupedTasks.values.flatten()
            else
                filteredTasks

            if (allTasks.isEmpty()) {
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    if (viewMode == ViewMode.UPCOMING) {
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
                                    navController = navController,
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
                                navController = navController,
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
    navController: NavController,
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
                    onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute(task.id)) },
                    onLongClick = { viewModel.onEnterMultiSelect(task.id) },
                    onAddSubtaskClick = {
                        onExpandChange(expandedTaskIds + task.id)
                        onFocusChange(task.id)
                    }
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
                onAddSubtask = viewModel::onAddSubtask,
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
    onAddSubtaskClick: () -> Unit
) {
    val isOverdue = isTaskOverdue(task)
    val borderColor = if (isOverdue) OverdueRed else Color.Transparent

    Card(
        modifier = Modifier
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

