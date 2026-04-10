package com.averycorp.averytask.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.averycorp.averytask.data.repository.HabitWithStatus
import androidx.compose.material3.AlertDialog
import com.averycorp.averytask.ui.components.MoveToProjectSheet
import com.averycorp.averytask.ui.components.QuickAddBar
import com.averycorp.averytask.ui.components.QuickReschedulePopup
import com.averycorp.averytask.ui.navigation.AveryTaskRoute
import com.averycorp.averytask.ui.screens.addedittask.AddEditTaskSheetHost
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OverdueRed = Color(0xFFD93025)
private val CompletedGreen = Color(0xFF4CAF50)

private const val SECTION_OVERDUE = "overdue"
private const val SECTION_TODAY_TASKS = "today_tasks"
private const val SECTION_HABITS = "habits"
private const val SECTION_PLANNED = "planned"
private const val SECTION_COMPLETED = "completed"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    navController: NavController,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val overdueTasks by viewModel.overdueTasks.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val plannedTasks by viewModel.plannedTasks.collectAsStateWithLifecycle()
    val completedToday by viewModel.completedToday.collectAsStateWithLifecycle()
    val taskTagsMap by viewModel.taskTagsMap.collectAsStateWithLifecycle()
    val showPlanSheet by viewModel.showPlanSheet.collectAsStateWithLifecycle()
    val tasksNotInToday by viewModel.tasksNotInToday.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val startOfToday by viewModel.startOfToday.collectAsStateWithLifecycle()
    val todayHabits by viewModel.todayHabits.collectAsStateWithLifecycle()
    val combinedTotal by viewModel.combinedTotal.collectAsStateWithLifecycle()
    val combinedCompleted by viewModel.combinedCompleted.collectAsStateWithLifecycle()
    val combinedProgress by viewModel.combinedProgress.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val collapsedSections by viewModel.collapsedSections.collectAsStateWithLifecycle()
    val allHabitsCompleted by viewModel.allHabitsCompletedToday.collectAsStateWithLifecycle()
    val habitCompletedCount by viewModel.habitCompletedCount.collectAsStateWithLifecycle()
    val habitTotalCount by viewModel.habitTotalCount.collectAsStateWithLifecycle()

    val totalForHeader = combinedTotal
    val allTodayDone = remember(overdueTasks, todayTasks, plannedTasks, completedToday, allHabitsCompleted) {
        overdueTasks.isEmpty() && todayTasks.isEmpty() && plannedTasks.isEmpty() && completedToday.isNotEmpty() && allHabitsCompleted
    }

    var editorSheetTaskId by remember { mutableStateOf<Long?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var reschedulePopupTask by remember { mutableStateOf<TaskEntity?>(null) }
    var moveToProjectSheetTask by remember { mutableStateOf<TaskEntity?>(null) }
    var cascadeConfirmState by remember { mutableStateOf<Pair<TaskEntity, Long?>?>(null) }
    val taskCountByProject by viewModel.taskCountByProject.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        topBar = {
            CompactProgressHeader(
                completed = combinedCompleted,
                total = totalForHeader,
                progress = combinedProgress
            )
        },
        bottomBar = {
            FloatingQuickAddBar()
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorSheetTaskId = null
                    showEditorSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Task")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // All-Caught-Up celebration when there's nothing left to do (but at least one task done today)
            if (allTodayDone) {
                item(key = "all_caught_up") {
                    AllCaughtUpCard(
                        taskCount = completedToday.size,
                        habitCount = habitCompletedCount,
                        habitTotal = habitTotalCount,
                        onPlanTomorrow = { viewModel.onShowPlanSheet() }
                    )
                }
            }

            // Overdue (special urgent treatment)
            if (SECTION_OVERDUE !in hiddenSections && overdueTasks.isNotEmpty()) {
                val expanded = SECTION_OVERDUE !in collapsedSections
                item(key = "section_overdue") {
                    OverdueSectionContainer(
                        count = overdueTasks.size,
                        expanded = expanded,
                        onToggle = { viewModel.onToggleSectionCollapsed(SECTION_OVERDUE) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            overdueTasks.forEach { task ->
                                SwipeableTaskItem(
                                    task = task,
                                    tags = taskTagsMap[task.id].orEmpty(),
                                    isOverdue = true,
                                    onComplete = { viewModel.onCompleteWithUndo(task.id) },
                                    onClick = {
                                        editorSheetTaskId = task.id
                                        showEditorSheet = true
                                    },
                                    onReschedule = { reschedulePopupTask = task },
                                    onMoveToProject = { moveToProjectSheetTask = task },
                                    onDuplicate = { viewModel.onDuplicateTask(task.id) },
                                    onDelete = { viewModel.onDeleteTaskWithUndo(task.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Today Tasks (only items truly due today)
            if (SECTION_TODAY_TASKS !in hiddenSections && todayTasks.isNotEmpty()) {
                val expanded = SECTION_TODAY_TASKS !in collapsedSections
                item(key = "section_today_tasks") {
                    CollapsibleSection(
                        emoji = "\uD83D\uDCCB",
                        title = "Today Tasks",
                        count = todayTasks.size,
                        accentColor = MaterialTheme.colorScheme.primary,
                        expanded = expanded,
                        onToggle = { viewModel.onToggleSectionCollapsed(SECTION_TODAY_TASKS) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            todayTasks.forEach { task ->
                                SwipeableTaskItem(
                                    task = task,
                                    tags = taskTagsMap[task.id].orEmpty(),
                                    onComplete = { viewModel.onCompleteWithUndo(task.id) },
                                    onClick = {
                                        editorSheetTaskId = task.id
                                        showEditorSheet = true
                                    },
                                    onReschedule = { reschedulePopupTask = task },
                                    onMoveToProject = { moveToProjectSheetTask = task },
                                    onDuplicate = { viewModel.onDuplicateTask(task.id) },
                                    onDelete = { viewModel.onDeleteTaskWithUndo(task.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Habits — horizontal scrollable
            if (SECTION_HABITS !in hiddenSections && todayHabits.isNotEmpty()) {
                val expanded = SECTION_HABITS !in collapsedSections
                val habitCompletedCount = todayHabits.count { it.isCompletedToday }
                item(key = "section_habits") {
                    CollapsibleSection(
                        emoji = "\uD83D\uDCAA",
                        title = "Habits",
                        count = todayHabits.size,
                        countLabel = "$habitCompletedCount/${todayHabits.size}",
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        expanded = expanded,
                        onToggle = { viewModel.onToggleSectionCollapsed(SECTION_HABITS) }
                    ) {
                        HabitChipRow(
                            habits = todayHabits,
                            onToggle = { hws ->
                                viewModel.onToggleHabitCompletion(hws.habit.id, hws.isCompletedToday)
                            },
                            onSeeAll = { navController.navigate(AveryTaskRoute.HabitList.route) }
                        )
                    }
                }
            }

            // Planned for Today (separate from due-today tasks)
            if (SECTION_PLANNED !in hiddenSections && plannedTasks.isNotEmpty()) {
                val expanded = SECTION_PLANNED !in collapsedSections
                item(key = "section_planned") {
                    CollapsibleSection(
                        emoji = "\uD83D\uDCCC",
                        title = "Planned",
                        count = plannedTasks.size,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        expanded = expanded,
                        onToggle = { viewModel.onToggleSectionCollapsed(SECTION_PLANNED) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            plannedTasks.forEach { task ->
                                SwipeableTaskItem(
                                    task = task,
                                    tags = taskTagsMap[task.id].orEmpty(),
                                    isPlanned = true,
                                    onComplete = { viewModel.onCompleteWithUndo(task.id) },
                                    onClick = {
                                        editorSheetTaskId = task.id
                                        showEditorSheet = true
                                    },
                                    onReschedule = { reschedulePopupTask = task },
                                    onMoveToProject = { moveToProjectSheetTask = task },
                                    onDuplicate = { viewModel.onDuplicateTask(task.id) },
                                    onDelete = { viewModel.onDeleteTaskWithUndo(task.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Plan more shortcut — only if there's space to plan
            item(key = "plan_more") {
                FilledTonalButton(
                    onClick = { viewModel.onShowPlanSheet() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan More")
                }
            }

            // Completed
            if (SECTION_COMPLETED !in hiddenSections && completedToday.isNotEmpty()) {
                val expanded = SECTION_COMPLETED !in collapsedSections
                item(key = "section_completed") {
                    CollapsibleSection(
                        emoji = "\u2705",
                        title = "Completed",
                        count = completedToday.size,
                        accentColor = CompletedGreen,
                        expanded = expanded,
                        onToggle = { viewModel.onToggleSectionCollapsed(SECTION_COMPLETED) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            completedToday.forEach { task ->
                                CompletedTaskItem(
                                    task = task,
                                    onUncomplete = { viewModel.onToggleComplete(task.id, true) }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom padding so the last item doesn't sit under the floating bar / FAB
            item(key = "bottom_pad") {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }

    // Plan for Today bottom sheet
    val topTemplates by viewModel.topTemplates.collectAsStateWithLifecycle()
    if (showPlanSheet) {
        PlanForTodaySheet(
            plannedTasks = plannedTasks,
            overdueTasks = overdueTasks,
            upcomingTasks = tasksNotInToday,
            projects = projects,
            startOfToday = startOfToday,
            topTemplates = topTemplates,
            onPlan = { viewModel.onPlanForToday(it) },
            onPlanMany = { viewModel.onPlanForToday(it) },
            onPlanAllOverdue = { viewModel.onPlanAllOverdue() },
            onUnplan = { viewModel.onRemoveFromToday(it) },
            onUseTemplate = { viewModel.onCreateTaskFromTemplateForToday(it) },
            onOpenManageTemplates = {
                viewModel.onDismissPlanSheet()
                navController.navigate(AveryTaskRoute.TemplateList.route)
            },
            onDismiss = { viewModel.onDismissPlanSheet() }
        )
    }

    if (showEditorSheet) {
        AddEditTaskSheetHost(
            taskId = editorSheetTaskId,
            projectId = null,
            initialDate = if (editorSheetTaskId == null) startOfToday else null,
            onDismiss = { showEditorSheet = false },
            onDeleteTask = { id -> viewModel.onDeleteTaskWithUndo(id) },
            onManageTemplates = {
                showEditorSheet = false
                navController.navigate(AveryTaskRoute.TemplateList.route)
            }
        )
    }

    reschedulePopupTask?.let { task ->
        QuickReschedulePopup(
            hasDueDate = task.dueDate != null,
            onDismiss = { reschedulePopupTask = null },
            onReschedule = { newDate -> viewModel.onRescheduleTask(task.id, newDate) },
            onPlanForToday = { viewModel.onPlanTaskForToday(task.id) }
        )
    }

    moveToProjectSheetTask?.let { task ->
        var subtaskCount by remember(task.id) { mutableStateOf(0) }
        LaunchedEffect(task.id) {
            subtaskCount = viewModel.getSubtaskCount(task.id)
        }
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
            text = { Text("'${task.title}' has subtasks. Should they move to the same project?") },
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

/**
 * Sticky compact header bar shown in the Scaffold topBar slot.
 *  - Left: "Today" + date subtitle
 *  - Center: thin (4dp) horizontal progress bar
 *  - Right: "completed/total" text
 *  Briefly scales the progress bar and flashes green when all items are done.
 */
@Composable
private fun CompactProgressHeader(
    completed: Int,
    total: Int,
    progress: Float
) {
    val dateLabel = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "headerProgress"
    )

    // Brief celebration when all tasks complete: flash green + scale up the bar.
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(progress >= 1f && total > 0) {
        if (progress >= 1f && total > 0) {
            celebrate = true
            delay(900)
            celebrate = false
        }
    }
    val barColor by animateColorAsState(
        targetValue = when {
            progress >= 1f -> CompletedGreen
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(400),
        label = "headerBarColor"
    )
    val barScale by animateFloatAsState(
        targetValue = if (celebrate) 1.6f else 1f,
        animationSpec = tween(350),
        label = "headerBarScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(120.dp)) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            LinearProgressIndicator(
                progress = { animatedProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height((4f * barScale).dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "$completed/$total",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Centered "all caught up" celebration shown when there are no remaining
 * overdue/today/planned tasks AND all habits are completed for the day.
 */
@Composable
private fun AllCaughtUpCard(
    taskCount: Int,
    habitCount: Int,
    habitTotal: Int,
    onPlanTomorrow: () -> Unit
) {
    val subtitle = if (habitTotal > 0) {
        "You completed $taskCount task${if (taskCount != 1) "s" else ""} and $habitCount habit${if (habitCount != 1) "s" else ""} today"
    } else {
        "You completed $taskCount task${if (taskCount != 1) "s" else ""} today"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CompletedGreen.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83C\uDF89",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "All Caught Up!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onPlanTomorrow) {
                Text("Plan Tomorrow")
            }
        }
    }
}

/**
 * A reusable collapsible section with an emoji header, count badge, and chevron.
 * Animates content size on expand/collapse.
 */
@Composable
private fun CollapsibleSection(
    emoji: String,
    title: String,
    count: Int,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    countLabel: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(280))
    ) {
        SectionHeaderRow(
            emoji = emoji,
            title = title,
            count = count,
            countLabel = countLabel,
            accentColor = accentColor,
            expanded = expanded,
            onToggle = onToggle
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

/**
 * The Overdue section gets special urgency treatment: red tinted background,
 * left red accent bar, and a red count badge.
 */
@Composable
private fun OverdueSectionContainer(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val barWidthDp = 4.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .drawBehind {
                // Left red accent bar drawn at draw-time so it always matches
                // the (animated) measured height.
                drawRect(
                    color = OverdueRed,
                    topLeft = Offset.Zero,
                    size = Size(barWidthDp.toPx(), size.height)
                )
            }
            .padding(start = barWidthDp + 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .animateContentSize(animationSpec = tween(280))
    ) {
        SectionHeaderRow(
            emoji = "\uD83D\uDEA8",
            title = "Overdue",
            count = count,
            countLabel = null,
            accentColor = OverdueRed,
            expanded = expanded,
            onToggle = onToggle,
            badgeStyle = BadgeStyle.RED_CIRCLE,
            prominentWhenCollapsed = true
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            content()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private enum class BadgeStyle { DEFAULT, RED_CIRCLE }

@Composable
private fun SectionHeaderRow(
    emoji: String,
    title: String,
    count: Int,
    countLabel: String?,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    badgeStyle: BadgeStyle = BadgeStyle.DEFAULT,
    prominentWhenCollapsed: Boolean = false
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(220),
        label = "chevronRotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Count badge
        when (badgeStyle) {
            BadgeStyle.RED_CIRCLE -> {
                val prominent = prominentWhenCollapsed && !expanded && count > 0
                Box(
                    modifier = Modifier
                        .size(if (prominent) 24.dp else 20.dp)
                        .clip(CircleShape)
                        .background(OverdueRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = if (prominent) 13.sp else 11.sp
                    )
                }
            }
            BadgeStyle.DEFAULT -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = countLabel ?: "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Horizontal scrollable habit chips. Each chip shows the habit's emoji, name,
 * and a small circular progress indicator. The trailing chip is a "See All"
 * shortcut to the full habits list.
 */
@Composable
private fun HabitChipRow(
    habits: List<HabitWithStatus>,
    onToggle: (HabitWithStatus) -> Unit,
    onSeeAll: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(habits, key = { "habit_chip_${it.habit.id}" }) { hws ->
            HabitChip(
                habitWithStatus = hws,
                onTap = { onToggle(hws) }
            )
        }
        item(key = "habit_see_all") {
            SeeAllChip(onClick = onSeeAll)
        }
    }
}

@Composable
private fun HabitChip(
    habitWithStatus: HabitWithStatus,
    onTap: () -> Unit
) {
    val habit = habitWithStatus.habit
    val habitColor = remember(habit.color) {
        try {
            Color(android.graphics.Color.parseColor(habit.color))
        } catch (_: Exception) {
            Color(0xFF4A90D9)
        }
    }
    val isComplete = habitWithStatus.isCompletedToday
    val target = habitWithStatus.dailyTarget.coerceAtLeast(1)
    val done = habitWithStatus.completionsToday.coerceAtMost(target)
    val ringProgress = if (isComplete) 1f else done.toFloat() / target.toFloat()

    val containerColor = if (isComplete) {
        habitColor.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        modifier = Modifier
            .width(118.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { ringProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp),
                    color = if (isComplete) CompletedGreen else habitColor,
                    trackColor = habitColor.copy(alpha = 0.18f),
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = habit.icon,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = habit.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (target > 1 && !isComplete) {
                Text(
                    text = "$done/$target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isComplete) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelSmall,
                    color = CompletedGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SeeAllChip(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(96.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Floating QuickAddBar pinned to the bottom of the screen above the nav bar.
 * Uses a semi-transparent surface so content faintly shows through.
 */
@Composable
private fun FloatingQuickAddBar() {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            QuickAddBar()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableTaskItem(
    task: TaskEntity,
    tags: List<TagEntity>,
    isOverdue: Boolean = false,
    isPlanned: Boolean = false,
    onComplete: () -> Unit,
    onClick: () -> Unit,
    onReschedule: () -> Unit = {},
    onMoveToProject: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onComplete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CompletedGreen)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        },
        enableDismissFromEndToStart = false
    ) {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = false,
                    onCheckedChange = { onComplete() }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.priority > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LocalPriorityColors.current.forLevel(task.priority))
                            )
                        }
                        if (isOverdue && task.dueDate != null) {
                            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                            Text(
                                text = fmt.format(Date(task.dueDate)),
                                style = MaterialTheme.typography.labelSmall,
                                color = OverdueRed
                            )
                        }
                        if (isPlanned && task.dueDate != null) {
                            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                            Text(
                                text = "Due: ${fmt.format(Date(task.dueDate))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isPlanned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Planned",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        tags.take(3).forEach { tag ->
                            val tagColor = try {
                                Color(android.graphics.Color.parseColor(tag.color))
                            } catch (_: Exception) { MaterialTheme.colorScheme.outline }
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tagColor
                            )
                        }
                    }
                }
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
                            text = { Text("\uD83D\uDCC5  Reschedule") },
                            onClick = {
                                showOverflowMenu = false
                                onReschedule()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCC1  Move To Project") },
                            onClick = {
                                showOverflowMenu = false
                                onMoveToProject()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCCB  Duplicate") },
                            onClick = {
                                showOverflowMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDDD1\uFE0F  Delete") },
                            onClick = {
                                showOverflowMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedTaskItem(task: TaskEntity, onUncomplete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUncomplete() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = true, onCheckedChange = { onUncomplete() })
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class PlanSortMode { PRIORITY, DUE_DATE, PROJECT }

private data class PlanGroup(
    val key: String,
    val title: String,
    val tasks: List<TaskEntity>,
    val expanded: Boolean,
    val onToggle: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlanForTodaySheet(
    plannedTasks: List<TaskEntity>,
    overdueTasks: List<TaskEntity>,
    upcomingTasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    startOfToday: Long,
    topTemplates: List<com.averycorp.averytask.data.local.entity.TaskTemplateEntity>,
    onPlan: (Long) -> Unit,
    onPlanMany: (List<Long>) -> Unit,
    onPlanAllOverdue: () -> Unit,
    onUnplan: (Long) -> Unit,
    onUseTemplate: (Long) -> Unit,
    onOpenManageTemplates: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlanSortMode.DUE_DATE) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var showTemplatePickerSheet by remember { mutableStateOf(false) }

    // Collapsible group state
    var overdueExpanded by remember { mutableStateOf(true) }
    var tomorrowExpanded by remember { mutableStateOf(true) }
    var thisWeekExpanded by remember { mutableStateOf(true) }
    var nextWeekExpanded by remember { mutableStateOf(true) }
    var laterExpanded by remember { mutableStateOf(false) }
    var noDateExpanded by remember { mutableStateOf(false) }

    val projectsById = remember(projects) { projects.associateBy { it.id } }

    // Filter by search query
    val filter: (TaskEntity) -> Boolean = { task ->
        searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true)
    }

    // Apply sort
    val sortComparator: Comparator<TaskEntity> = when (sortMode) {
        PlanSortMode.PRIORITY -> compareByDescending<TaskEntity> { it.priority }
            .thenBy { it.dueDate ?: Long.MAX_VALUE }
        PlanSortMode.DUE_DATE -> compareBy<TaskEntity> { it.dueDate ?: Long.MAX_VALUE }
            .thenByDescending { it.priority }
        PlanSortMode.PROJECT -> compareBy<TaskEntity> {
            it.projectId?.let { id -> projectsById[id]?.name } ?: "zzz"
        }.thenByDescending { it.priority }
    }

    val filteredUpcoming = upcomingTasks.filter(filter).sortedWith(sortComparator)
    val filteredOverdue = overdueTasks.filter(filter).sortedWith(sortComparator)
    val filteredPlanned = plannedTasks.filter(filter).sortedWith(sortComparator)

    // Group upcoming by timing buckets
    val dayMs = 86_400_000L
    val tomorrowStart = startOfToday + dayMs
    val dayAfterTomorrowStart = tomorrowStart + dayMs
    val thisWeekEnd = startOfToday + 7 * dayMs
    val nextWeekEnd = startOfToday + 14 * dayMs

    val tomorrowTasks = filteredUpcoming.filter { it.dueDate != null && it.dueDate in tomorrowStart until dayAfterTomorrowStart }
    val thisWeekTasks = filteredUpcoming.filter { it.dueDate != null && it.dueDate in dayAfterTomorrowStart until thisWeekEnd }
    val nextWeekTasks = filteredUpcoming.filter { it.dueDate != null && it.dueDate in thisWeekEnd until nextWeekEnd }
    val laterTasks = filteredUpcoming.filter { it.dueDate != null && it.dueDate >= nextWeekEnd }
    val noDateTasks = filteredUpcoming.filter { it.dueDate == null }

    val hasAnyUpcoming = filteredOverdue.isNotEmpty() || tomorrowTasks.isNotEmpty() ||
            thisWeekTasks.isNotEmpty() || nextWeekTasks.isNotEmpty() ||
            laterTasks.isNotEmpty() || noDateTasks.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plan for Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (multiSelectMode) {
                    TextButton(onClick = {
                        multiSelectMode = false
                        selectedIds = emptySet()
                    }) {
                        Text("Cancel")
                    }
                }
            }

            // Templates section — shows top-used templates as compact chips
            // for one-tap "plan this from template" creation. Empty when the
            // user hasn't created any templates yet (no disruption).
            if (topTemplates.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "\uD83D\uDCCB Templates",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(topTemplates, key = { it.id }) { template ->
                            TemplateQuickChip(
                                icon = template.icon,
                                label = template.name,
                                onClick = { onUseTemplate(template.id) }
                            )
                        }
                        item {
                            TextButton(
                                onClick = { showTemplatePickerSheet = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "More Templates...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Inline quick-add (tasks here get planned for today immediately)
            QuickAddBar(
                viewModel = hiltViewModel(key = "plan_sheet_quickadd"),
                plannedDateOverride = startOfToday,
                alwaysExpanded = true,
                placeholder = "Add task for today..."
            )

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search tasks...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sort chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanSortMode.values().forEach { mode ->
                    val label = when (mode) {
                        PlanSortMode.PRIORITY -> "Priority"
                        PlanSortMode.DUE_DATE -> "Due Date"
                        PlanSortMode.PROJECT -> "Project"
                    }
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val showEmptyState = filteredPlanned.isEmpty() && !hasAnyUpcoming

            if (showEmptyState) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank())
                            "No tasks match your search"
                        else
                            "No upcoming tasks to plan. Create one above!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Planned for Today section
                    if (filteredPlanned.isNotEmpty()) {
                        item(key = "hdr_planned") {
                            PlanGroupHeader(
                                title = "\uD83D\uDCCC Planned for Today",
                                count = filteredPlanned.size,
                                color = MaterialTheme.colorScheme.primary,
                                expanded = true,
                                onToggle = null
                            )
                        }
                        items(filteredPlanned, key = { "planned_${it.id}" }) { task ->
                            SheetTaskCard(
                                task = task,
                                project = task.projectId?.let { projectsById[it] },
                                isPlanned = true,
                                multiSelectMode = multiSelectMode,
                                isSelected = task.id in selectedIds,
                                onTap = {
                                    if (multiSelectMode) {
                                        selectedIds = if (task.id in selectedIds)
                                            selectedIds - task.id else selectedIds + task.id
                                    } else {
                                        onUnplan(task.id)
                                    }
                                },
                                onLongPress = {
                                    if (!multiSelectMode) {
                                        multiSelectMode = true
                                        selectedIds = setOf(task.id)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    // Overdue group with "Plan All" shortcut
                    if (filteredOverdue.isNotEmpty()) {
                        item(key = "hdr_overdue") {
                            PlanGroupHeader(
                                title = "Overdue",
                                count = filteredOverdue.size,
                                color = OverdueRed,
                                expanded = overdueExpanded,
                                onToggle = { overdueExpanded = !overdueExpanded },
                                trailing = {
                                    TextButton(
                                        onClick = { onPlanAllOverdue() },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(
                                            "Plan All",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OverdueRed
                                        )
                                    }
                                }
                            )
                        }
                        if (overdueExpanded) {
                            items(filteredOverdue, key = { "overdue_${it.id}" }) { task ->
                                SheetTaskCard(
                                    task = task,
                                    project = task.projectId?.let { projectsById[it] },
                                    isPlanned = false,
                                    isOverdue = true,
                                    multiSelectMode = multiSelectMode,
                                    isSelected = task.id in selectedIds,
                                    onTap = {
                                        if (multiSelectMode) {
                                            selectedIds = if (task.id in selectedIds)
                                                selectedIds - task.id else selectedIds + task.id
                                        } else {
                                            onPlan(task.id)
                                        }
                                    },
                                    onLongPress = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedIds = setOf(task.id)
                                        }
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    // Upcoming groups (key, title, tasks, expanded, onToggle)
                    val groups: List<PlanGroup> = listOf(
                        PlanGroup("tomorrow", "Tomorrow", tomorrowTasks, tomorrowExpanded) { tomorrowExpanded = !tomorrowExpanded },
                        PlanGroup("this_week", "This Week", thisWeekTasks, thisWeekExpanded) { thisWeekExpanded = !thisWeekExpanded },
                        PlanGroup("next_week", "Next Week", nextWeekTasks, nextWeekExpanded) { nextWeekExpanded = !nextWeekExpanded },
                        PlanGroup("later", "Later", laterTasks, laterExpanded) { laterExpanded = !laterExpanded },
                        PlanGroup("no_date", "No Date", noDateTasks, noDateExpanded) { noDateExpanded = !noDateExpanded }
                    )

                    groups.forEach { group ->
                        if (group.tasks.isEmpty()) return@forEach
                        item(key = "hdr_${group.key}") {
                            PlanGroupHeader(
                                title = group.title,
                                count = group.tasks.size,
                                color = MaterialTheme.colorScheme.primary,
                                expanded = group.expanded,
                                onToggle = group.onToggle
                            )
                        }
                        if (group.expanded) {
                            items(group.tasks, key = { "${group.key}_${it.id}" }) { task ->
                                SheetTaskCard(
                                    task = task,
                                    project = task.projectId?.let { projectsById[it] },
                                    isPlanned = false,
                                    multiSelectMode = multiSelectMode,
                                    isSelected = task.id in selectedIds,
                                    onTap = {
                                        if (multiSelectMode) {
                                            selectedIds = if (task.id in selectedIds)
                                                selectedIds - task.id else selectedIds + task.id
                                        } else {
                                            onPlan(task.id)
                                        }
                                    },
                                    onLongPress = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedIds = setOf(task.id)
                                        }
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    item(key = "bottom_pad") {
                        Spacer(modifier = Modifier.height(if (multiSelectMode) 80.dp else 16.dp))
                    }
                }
            }

            // Batch planning bar
            AnimatedVisibility(
                visible = multiSelectMode && selectedIds.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onPlanMany(selectedIds.toList())
                            selectedIds = emptySet()
                            multiSelectMode = false
                        }
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Plan Selected (${selectedIds.size})")
                    }
                }
            }
        }
    }

    // Full template picker overlay — shown when the user taps "More Templates..."
    // on the chip row. Delegates to the shared TemplatePickerSheet so the UI
    // matches what the task editor shows.
    if (showTemplatePickerSheet) {
        com.averycorp.averytask.ui.screens.templates.TemplatePickerSheet(
            onDismiss = { showTemplatePickerSheet = false },
            onUseTemplate = { template ->
                onUseTemplate(template.id)
                showTemplatePickerSheet = false
            },
            onManageTemplates = {
                showTemplatePickerSheet = false
                onOpenManageTemplates()
            }
        )
    }
}

/**
 * Compact chip surfaced in the Plan-for-Today sheet's Templates row. One
 * tap fires [onClick], which invokes the ViewModel's
 * `onCreateTaskFromTemplateForToday` and drops the resulting task straight
 * onto today's dashboard.
 */
@Composable
private fun TemplateQuickChip(
    icon: String?,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon ?: "\uD83D\uDCCB",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlanGroupHeader(
    title: String,
    count: Int,
    color: Color,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else if (onToggle != null) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetTaskCard(
    task: TaskEntity,
    project: ProjectEntity?,
    isPlanned: Boolean,
    isOverdue: Boolean = false,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isPlanned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        isOverdue -> OverdueRed.copy(alpha = 0.06f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (task.priority > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LocalPriorityColors.current.forLevel(task.priority))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.dueDate != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateFormat.format(Date(task.dueDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) OverdueRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (project != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isPlanned) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\uD83D\uDCCC Planned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
