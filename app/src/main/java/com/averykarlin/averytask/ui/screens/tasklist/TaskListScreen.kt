package com.averykarlin.averytask.ui.screens.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.ui.components.EmptyState
import com.averykarlin.averytask.ui.components.SubtaskSection
import com.averykarlin.averytask.ui.navigation.AveryTaskRoute
import com.averykarlin.averytask.ui.theme.PriorityColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val OverdueRed = Color(0xFFD93025)
private val TodayOrange = Color(0xFFE8872A)

@OptIn(ExperimentalMaterial3Api::class)
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
    val currentSort by viewModel.currentSort.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val overdueCount by viewModel.overdueCount.collectAsStateWithLifecycle()
    var expandedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var focusSubtaskForId by remember { mutableStateOf<Long?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AveryTask",
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
                    IconButton(onClick = {
                        viewModel.onChangeViewMode(
                            if (viewMode == ViewMode.UPCOMING) ViewMode.LIST else ViewMode.UPCOMING
                        )
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.UPCOMING)
                                Icons.Default.FormatListBulleted
                            else
                                Icons.Default.Schedule,
                            contentDescription = if (viewMode == ViewMode.UPCOMING) "List view" else "Upcoming view"
                        )
                    }
                    IconButton(onClick = { navController.navigate(AveryTaskRoute.ProjectList.route) }) {
                        Icon(
                            imageVector = Icons.Default.FolderCopy,
                            contentDescription = "Projects"
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
        },
        floatingActionButton = {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (projects.isNotEmpty()) {
                ProjectFilterRow(
                    projects = projects,
                    selectedProjectId = selectedProjectId,
                    onSelectProject = viewModel::onSelectProject
                )
            }

            val allTasks = if (viewMode == ViewMode.UPCOMING)
                groupedTasks.values.flatten()
            else
                filteredTasks

            if (allTasks.isEmpty()) {
                if (selectedProjectId != null) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No tasks match your filters",
                        subtitle = "Try selecting a different project",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    EmptyState(
                        icon = Icons.Default.CheckBoxOutlineBlank,
                        title = "No tasks yet",
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
                                    expandedTaskIds = expandedTaskIds,
                                    focusSubtaskForId = focusSubtaskForId,
                                    navController = navController,
                                    viewModel = viewModel,
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
                                expandedTaskIds = expandedTaskIds,
                                focusSubtaskForId = focusSubtaskForId,
                                navController = navController,
                                viewModel = viewModel,
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

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.taskItemWithSubtasks(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    subtasksMap: Map<Long, List<TaskEntity>>,
    expandedTaskIds: Set<Long>,
    focusSubtaskForId: Long?,
    navController: NavController,
    viewModel: TaskListViewModel,
    onExpandChange: (Set<Long>) -> Unit,
    onFocusChange: (Long?) -> Unit
) {
    val subtasks = subtasksMap[task.id].orEmpty()
    item(key = task.id) {
        val project = projects.find { it.id == task.projectId }
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
                onToggleComplete = { viewModel.onToggleComplete(task.id, task.isCompleted) },
                onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute(task.id)) },
                onAddSubtaskClick = {
                    onExpandChange(expandedTaskIds + task.id)
                    onFocusChange(task.id)
                }
            )
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
    onSelectProject: (Long?) -> Unit
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
    }
}

@Composable
private fun TaskItem(
    task: TaskEntity,
    project: ProjectEntity?,
    subtasks: List<TaskEntity>,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onAddSubtaskClick: () -> Unit
) {
    val isOverdue = isTaskOverdue(task)
    val borderColor = if (isOverdue) OverdueRed else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                OverdueRed.copy(alpha = 0.06f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
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
                checked = task.isCompleted,
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
            .background(PriorityColors.forLevel(priority))
    )
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

