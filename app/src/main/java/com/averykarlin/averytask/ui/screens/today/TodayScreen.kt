package com.averykarlin.averytask.ui.screens.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.HabitWithStatus
import com.averykarlin.averytask.ui.components.EmptyState
import com.averykarlin.averytask.ui.components.QuickAddBar
import com.averykarlin.averytask.ui.components.StreakBadge
import com.averykarlin.averytask.ui.navigation.AveryTaskRoute
import com.averykarlin.averytask.ui.theme.PriorityColors
import java.text.SimpleDateFormat
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
    val todayHabits by viewModel.todayHabits.collectAsStateWithLifecycle()
    val combinedTotal by viewModel.combinedTotal.collectAsStateWithLifecycle()
    val combinedCompleted by viewModel.combinedCompleted.collectAsStateWithLifecycle()
    val combinedProgress by viewModel.combinedProgress.collectAsStateWithLifecycle()

    var overdueExpanded by remember { mutableStateOf(true) }
    var habitsExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Today", fontWeight = FontWeight.Bold) },
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
                    title = "Nothing for today",
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
                    // Progress card (combined tasks + habits)
                    item(key = "progress") {
                        val taskRemaining = overdueTasks.size + todayTasks.size + plannedTasks.size
                        val habitRemaining = todayHabits.count { !it.isCompletedToday }
                        ProgressCard(
                            completed = combinedCompleted,
                            total = combinedTotal + combinedCompleted,
                            progress = combinedProgress,
                            subtitle = "${taskRemaining} task${if (taskRemaining != 1) "s" else ""} \u00B7 ${habitRemaining} habit${if (habitRemaining != 1) "s" else ""} remaining"
                        )
                    }

                    // Habits section
                    if (todayHabits.isNotEmpty()) {
                        val habitsDoneCount = todayHabits.count { it.isCompletedToday }
                        item(key = "header_habits") {
                            SectionHeader(
                                title = "Habits",
                                count = todayHabits.size,
                                color = MaterialTheme.colorScheme.tertiary,
                                expanded = habitsExpanded,
                                onToggle = { habitsExpanded = !habitsExpanded }
                            )
                        }
                        if (habitsExpanded) {
                            items(todayHabits, key = { "habit_${it.habit.id}" }) { hws ->
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

                    // Overdue section
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

                    // Today + Planned section
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

                    // Plan more button
                    item(key = "plan_more") {
                        TextButton(
                            onClick = { viewModel.onShowPlanSheet() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Plan more")
                        }
                    }

                    // Completed section
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

                    // Summary
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
            tasks = tasksNotInToday,
            onPlan = { viewModel.onPlanForToday(it) },
            onDismiss = { viewModel.onDismissPlanSheet() }
        )
    }
}

@Composable
private fun ProgressCard(completed: Int, total: Int, progress: Float, subtitle: String? = null) {
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
                                    .background(PriorityColors.forLevel(task.priority))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanForTodaySheet(
    tasks: List<TaskEntity>,
    onPlan: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val plannedIds = remember { mutableStateOf(setOf<Long>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Plan for Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "No more tasks to plan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Group by timing
                    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                    val grouped = tasks.groupBy { task ->
                        when {
                            task.dueDate == null -> "No Date"
                            else -> {
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                val today = cal.timeInMillis
                                val tomorrow = today + 86400000
                                val weekEnd = today + 7 * 86400000
                                when {
                                    task.dueDate < tomorrow + 86400000 -> "Tomorrow"
                                    task.dueDate < weekEnd -> "This Week"
                                    else -> "Later"
                                }
                            }
                        }
                    }

                    val order = listOf("Tomorrow", "This Week", "Later", "No Date")
                    order.filter { it in grouped }.forEach { group ->
                        item { Text(group, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                        items(grouped[group]!!, key = { "plan_${it.id}" }) { task ->
                            val isPlanned = task.id in plannedIds.value
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isPlanned) {
                                            onPlan(task.id)
                                            plannedIds.value = plannedIds.value + task.id
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPlanned)
                                        CompletedGreen.copy(alpha = 0.1f)
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (task.priority > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(PriorityColors.forLevel(task.priority))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (task.dueDate != null) {
                                        Text(
                                            text = dateFormat.format(Date(task.dueDate)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isPlanned) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Planned",
                                            modifier = Modifier.size(16.dp),
                                            tint = CompletedGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Done")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
