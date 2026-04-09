package com.averycorp.averytask.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.averycorp.averytask.ui.components.EmptyState
import com.averycorp.averytask.ui.components.QuickAddBar
import com.averycorp.averytask.ui.components.StreakBadge
import com.averycorp.averytask.ui.navigation.AveryTaskRoute
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val OverdueRed = Color(0xFFD93025)
private val CompletedGreen = Color(0xFF4CAF50)

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
    val sectionOrder by viewModel.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val progressStyle by viewModel.progressStyle.collectAsStateWithLifecycle()

    var overdueExpanded by remember { mutableStateOf(true) }
    var dailyHabitsExpanded by remember { mutableStateOf(true) }
    var recurringHabitsExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(false) }

    val recurringHabitPeriods = remember {
        setOf("weekly", "fortnightly", "monthly", "bimonthly", "quarterly")
    }
    val dailyHabits = remember(todayHabits) {
        todayHabits.filter { it.habit.frequencyPeriod == "daily" }
    }
    val recurringHabits = remember(todayHabits) {
        todayHabits.filter { it.habit.frequencyPeriod in recurringHabitPeriods }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        topBar = {
            val greeting = remember {
                when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                    in 0..11 -> "Good Morning"
                    in 12..16 -> "Good Afternoon"
                    else -> "Good Evening"
                }
            }
            TopAppBar(
                title = {
                    Column {
                        Text(greeting, fontWeight = FontWeight.Bold)
                        Text(
                            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            QuickAddBar()

            val allEmpty = overdueTasks.isEmpty() && todayTasks.isEmpty() && plannedTasks.isEmpty() && completedToday.isEmpty() && todayHabits.isEmpty()

            if (allEmpty) {
                EmptyState(
                    icon = Icons.Default.Check,
                    title = "Nothing for Today",
                    subtitle = "Plan tasks for today or add a new one",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sectionOrder.forEach { sectionKey ->
                        if (sectionKey in hiddenSections) return@forEach

                        when (sectionKey) {
                            "progress" -> {
                                item(key = "progress") {
                                    val taskRemaining = todayTasks.size + plannedTasks.size
                                    val habitRemaining = todayHabits.count { !it.isCompletedToday }
                                    ProgressCard(
                                        completed = combinedCompleted,
                                        total = combinedTotal + combinedCompleted,
                                        progress = combinedProgress,
                                        style = progressStyle,
                                        subtitle = "${taskRemaining} task${if (taskRemaining != 1) "s" else ""} \u00B7 ${habitRemaining} habit${if (habitRemaining != 1) "s" else ""} remaining"
                                    )
                                }
                            }

                            "habits" -> {
                                if (dailyHabits.isNotEmpty()) {
                                    item(key = "header_habits_daily") {
                                        SectionHeader(
                                            title = "Daily Habits",
                                            count = dailyHabits.size,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            expanded = dailyHabitsExpanded,
                                            onToggle = { dailyHabitsExpanded = !dailyHabitsExpanded }
                                        )
                                    }
                                    if (dailyHabitsExpanded) {
                                        items(dailyHabits, key = { "habit_daily_${it.habit.id}" }) { hws ->
                                            CompactHabitItem(
                                                habitWithStatus = hws,
                                                onToggle = {
                                                    viewModel.onToggleHabitCompletion(hws.habit.id, hws.isCompletedToday)
                                                },
                                                onClick = {
                                                    navController.navigate(
                                                        AveryTaskRoute.HabitAnalytics.createRoute(hws.habit.id)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                                if (recurringHabits.isNotEmpty()) {
                                    item(key = "header_habits_recurring") {
                                        SectionHeader(
                                            title = "Recurring Habits",
                                            count = recurringHabits.size,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            expanded = recurringHabitsExpanded,
                                            onToggle = { recurringHabitsExpanded = !recurringHabitsExpanded }
                                        )
                                    }
                                    if (recurringHabitsExpanded) {
                                        items(recurringHabits, key = { "habit_recurring_${it.habit.id}" }) { hws ->
                                            CompactHabitItem(
                                                habitWithStatus = hws,
                                                onToggle = {
                                                    viewModel.onToggleHabitCompletion(hws.habit.id, hws.isCompletedToday)
                                                },
                                                onClick = {
                                                    navController.navigate(
                                                        AveryTaskRoute.HabitAnalytics.createRoute(hws.habit.id)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            "overdue" -> {
                                if (overdueTasks.isNotEmpty()) {
                                    item(key = "header_overdue") {
                                        SectionHeader(
                                            title = "Overdue",
                                            count = overdueTasks.size,
                                            color = OverdueRed,
                                            expanded = overdueExpanded,
                                            onToggle = { overdueExpanded = !overdueExpanded }
                                        )
                                    }
                                    if (overdueExpanded) {
                                        items(overdueTasks, key = { it.id }) { task ->
                                            SwipeableTaskItem(
                                                task = task,
                                                tags = taskTagsMap[task.id].orEmpty(),
                                                isOverdue = true,
                                                onComplete = { viewModel.onCompleteWithUndo(task.id) },
                                                onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute(task.id)) }
                                            )
                                        }
                                    }
                                }
                            }

                            "today_tasks" -> {
                                val todayAndPlanned = todayTasks + plannedTasks
                                if (todayAndPlanned.isNotEmpty()) {
                                    item(key = "header_today") {
                                        SectionHeader(
                                            title = "Today",
                                            count = todayAndPlanned.size,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(todayAndPlanned, key = { it.id }) { task ->
                                        val isPlanned = task.plannedDate != null && task.dueDate?.let {
                                            val cal = java.util.Calendar.getInstance()
                                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                            cal.set(java.util.Calendar.MINUTE, 0)
                                            cal.set(java.util.Calendar.SECOND, 0)
                                            cal.set(java.util.Calendar.MILLISECOND, 0)
                                            val start = cal.timeInMillis
                                            val end = start + 24 * 60 * 60 * 1000
                                            it < start || it >= end
                                        } ?: (task.plannedDate != null)

                                        SwipeableTaskItem(
                                            task = task,
                                            tags = taskTagsMap[task.id].orEmpty(),
                                            isPlanned = isPlanned,
                                            onComplete = { viewModel.onCompleteWithUndo(task.id) },
                                            onClick = { navController.navigate(AveryTaskRoute.AddEditTask.createRoute(task.id)) }
                                        )
                                    }
                                }
                            }

                            "plan_more" -> {
                                item(key = "plan_more") {
                                    TextButton(
                                        onClick = { viewModel.onShowPlanSheet() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Plan More")
                                    }
                                }
                            }

                            "completed" -> {
                                if (completedToday.isNotEmpty()) {
                                    item(key = "header_completed") {
                                        SectionHeader(
                                            title = "Completed",
                                            count = completedToday.size,
                                            color = CompletedGreen,
                                            expanded = completedExpanded,
                                            onToggle = { completedExpanded = !completedExpanded }
                                        )
                                    }
                                    if (completedExpanded) {
                                        items(completedToday, key = { "done_${it.id}" }) { task ->
                                            CompletedTaskItem(
                                                task = task,
                                                onUncomplete = { viewModel.onToggleComplete(task.id, true) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Summary (always last)
                    item(key = "summary") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You completed $combinedCompleted item${if (combinedCompleted != 1) "s" else ""} today" +
                                    if (overdueTasks.isNotEmpty()) " \u00B7 ${overdueTasks.size} overdue" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Plan for Today bottom sheet
    if (showPlanSheet) {
        PlanForTodaySheet(
            plannedTasks = plannedTasks,
            overdueTasks = overdueTasks,
            upcomingTasks = tasksNotInToday,
            projects = projects,
            startOfToday = startOfToday,
            onPlan = { viewModel.onPlanForToday(it) },
            onPlanMany = { viewModel.onPlanForToday(it) },
            onPlanAllOverdue = { viewModel.onPlanAllOverdue() },
            onUnplan = { viewModel.onRemoveFromToday(it) },
            onDismiss = { viewModel.onDismissPlanSheet() }
        )
    }
}

@Composable
private fun ProgressCard(completed: Int, total: Int, progress: Float, style: String = "ring", subtitle: String? = null) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "progress"
    )
    val ringColor by animateColorAsState(
        targetValue = if (progress >= 1f) CompletedGreen else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400),
        label = "ringColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (style) {
                "bar" -> {
                    Text(
                        text = if (progress >= 1f) "Done!" else "$completed/$total",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ringColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = ringColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
                "percentage" -> {
                    Text(
                        text = if (progress >= 1f) "Done!" else "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = ringColor
                    )
                }
                else -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(100.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = if (progress >= 1f) "Done!" else "$completed/$total",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ringColor
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Today's Progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CompactHabitItem(
    habitWithStatus: HabitWithStatus,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val habit = habitWithStatus.habit
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.color))
    } catch (_: Exception) { Color(0xFF4A90D9) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = habit.icon, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = habit.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (habitWithStatus.dailyTarget > 1 && !habitWithStatus.isCompletedToday) {
                Text(
                    text = "${habitWithStatus.completionsToday}/${habitWithStatus.dailyTarget}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (habitWithStatus.currentStreak > 0) {
                StreakBadge(streak = habitWithStatus.currentStreak)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Checkbox(
                checked = habitWithStatus.isCompletedToday,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    color: Color,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = 0.7f)
        )
        if (onToggle != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskItem(
    task: TaskEntity,
    tags: List<TagEntity>,
    isOverdue: Boolean = false,
    isPlanned: Boolean = false,
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
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
                .clickable { onClick() },
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
    onPlan: (Long) -> Unit,
    onPlanMany: (List<Long>) -> Unit,
    onPlanAllOverdue: () -> Unit,
    onUnplan: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlanSortMode.DUE_DATE) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var multiSelectMode by remember { mutableStateOf(false) }

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
