package com.averycorp.prismtask.ui.screens.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.repository.HabitWithStatus
import com.averycorp.prismtask.ui.components.RichEmptyState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.averycorp.prismtask.ui.components.StreakBadge
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    navController: NavController,
    viewModel: HabitListViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var habitToDelete by remember { mutableStateOf<HabitWithStatus?>(null) }
    var loggingHabit by remember { mutableStateOf<HabitWithStatus?>(null) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Daily", "Recurring")

    val recurringPeriods = setOf("weekly", "fortnightly", "monthly", "bimonthly", "quarterly")
    val filteredItems = remember(items, selectedTab) {
        if (selectedTab == 0) {
            items.filter { item ->
                when (item) {
                    is HabitListItem.HabitItem -> item.habitWithStatus.habit.frequencyPeriod == "daily"
                    is HabitListItem.SelfCareItem -> true
                    is HabitListItem.BuiltInHabitItem -> true
                }
            }
        } else {
            items.filter { item ->
                item is HabitListItem.HabitItem && item.habitWithStatus.habit.frequencyPeriod in recurringPeriods
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Habits", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(PrismTaskRoute.AddEditHabit.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Habit",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromItemIndex = items.indexOfFirst { it.key == from.key }
            val toItemIndex = items.indexOfFirst { it.key == to.key }
            if (fromItemIndex >= 0 && toItemIndex >= 0) {
                viewModel.onReorderItems(fromItemIndex, toItemIndex)
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                RichEmptyState(
                    icon = "\uD83D\uDD25",
                    title = "Start Building Habits",
                    description = "Track daily routines and watch your streaks grow.",
                    actionLabel = "Create Habit",
                    onAction = { navController.navigate(PrismTaskRoute.AddEditHabit.createRoute()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(filteredItems, key = { it.key }) { listItem ->
                    ReorderableItem(reorderableLazyListState, key = listItem.key) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        when (listItem) {
                            is HabitListItem.HabitItem -> {
                                HabitItem(
                                    habitWithStatus = listItem.habitWithStatus,
                                    onToggle = {
                                        val hws = listItem.habitWithStatus
                                        if (hws.habit.hasLogging && !hws.isCompletedToday) {
                                            loggingHabit = hws
                                        } else {
                                            viewModel.onToggleCompletion(hws.habit.id, hws.isCompletedToday)
                                        }
                                    },
                                    onDecrement = {
                                        val hws = listItem.habitWithStatus
                                        if (hws.completionsToday > 0 && hws.dailyTarget > 1) {
                                            viewModel.onDecrementCompletion(hws.habit.id)
                                        }
                                    },
                                    onClick = {
                                        if (listItem.habitWithStatus.habit.hasLogging) {
                                            loggingHabit = listItem.habitWithStatus
                                        } else {
                                            navController.navigate(
                                                PrismTaskRoute.HabitAnalytics.createRoute(listItem.habitWithStatus.habit.id)
                                            )
                                        }
                                    },
                                    onEdit = {
                                        navController.navigate(
                                            PrismTaskRoute.AddEditHabit.createRoute(listItem.habitWithStatus.habit.id)
                                        )
                                    },
                                    onDelete = { habitToDelete = listItem.habitWithStatus },
                                    modifier = Modifier
                                        .shadow(elevation, RoundedCornerShape(12.dp))
                                        .longPressDraggableHandle()
                                )
                            }
                            is HabitListItem.SelfCareItem -> {
                                SelfCareRoutineCard(
                                    routineType = listItem.routineType,
                                    cardData = listItem.cardData,
                                    onClick = {
                                        when (listItem.routineType) {
                                            "medication" -> navController.navigate(PrismTaskRoute.Medication.route)
                                            else -> navController.navigate(
                                                PrismTaskRoute.SelfCare.createRoute(listItem.routineType)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .shadow(elevation, RoundedCornerShape(12.dp))
                                        .longPressDraggableHandle()
                                )
                            }
                            is HabitListItem.BuiltInHabitItem -> {
                                BuiltInHabitCard(
                                    type = listItem.type,
                                    habitWithStatus = listItem.habitWithStatus,
                                    onClick = {
                                        when (listItem.type) {
                                            "school" -> navController.navigate(PrismTaskRoute.Schoolwork.route)
                                            "leisure" -> navController.navigate(PrismTaskRoute.Leisure.route)
                                        }
                                    },
                                    modifier = Modifier
                                        .shadow(elevation, RoundedCornerShape(12.dp))
                                        .longPressDraggableHandle()
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    habitToDelete?.let { hws ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text("Delete Habit") },
            text = { Text("Delete \"${hws.habit.name}\"? All completion history will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteHabit(hws.habit.id)
                    habitToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) { Text("Cancel") }
            }
        )
    }

    loggingHabit?.let { hws ->
        HabitLogDialog(
            habitWithStatus = hws,
            viewModel = viewModel,
            onDismiss = { loggingHabit = null }
        )
    }
}

@Composable
private fun HabitItem(
    habitWithStatus: HabitWithStatus,
    onToggle: () -> Unit,
    onDecrement: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStatus.habit
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.color))
    } catch (_: Exception) {
        Color(0xFF4A90D9)
    }

    val scale by animateFloatAsState(
        targetValue = if (habitWithStatus.isCompletedToday) 1.0f else 1.0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "checkScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(habitColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = habit.icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + streak info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (habit.hasLogging) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = "Logging enabled",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val periodLabel = when (habit.frequencyPeriod) {
                        "weekly" -> "this week"
                        "fortnightly" -> "this fortnight"
                        "monthly" -> "this month"
                        "bimonthly" -> "this period"
                        "quarterly" -> "this quarter"
                        else -> "this week"
                    }
                    val streakUnit = when (habit.frequencyPeriod) {
                        "weekly" -> "week streak"
                        "fortnightly" -> "fortnight streak"
                        "monthly" -> "month streak"
                        "bimonthly" -> "bimonth streak"
                        "quarterly" -> "quarter streak"
                        else -> "day streak"
                    }
                    if (habit.frequencyPeriod == "daily" && habitWithStatus.dailyTarget > 1) {
                        Text(
                            text = "${habitWithStatus.completionsToday}/${habitWithStatus.dailyTarget} today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (habitWithStatus.currentStreak > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            StreakBadge(streak = habitWithStatus.currentStreak)
                        }
                    } else if (habitWithStatus.currentStreak > 0) {
                        StreakBadge(streak = habitWithStatus.currentStreak)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = streakUnit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (habit.frequencyPeriod != "daily") {
                        Text(
                            text = "${habitWithStatus.completionsThisWeek}/${habit.targetFrequency} $periodLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "${habitWithStatus.completionsThisWeek}/${habit.targetFrequency} $periodLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress dots
                Spacer(modifier = Modifier.height(4.dp))
                val dotsTarget = when (habit.frequencyPeriod) {
                    "daily" -> 7
                    else -> habit.targetFrequency.coerceAtMost(7)
                }
                WeeklyDots(
                    completionsThisWeek = habitWithStatus.completionsThisWeek,
                    target = dotsTarget,
                    color = habitColor
                )

                // Booking / previous-period status badges (recurring habits only)
                if (habit.frequencyPeriod != "daily" &&
                    (habit.trackBooking || habit.trackPreviousPeriod)
                ) {
                    val periodNoun = when (habit.frequencyPeriod) {
                        "weekly" -> "week"
                        "fortnightly" -> "fortnight"
                        "monthly" -> "month"
                        "bimonthly" -> "period"
                        "quarterly" -> "quarter"
                        else -> "period"
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (habit.trackBooking) {
                            StatusPill(
                                label = if (habitWithStatus.isBookedThisPeriod) {
                                    if (habitWithStatus.bookedTasksThisPeriod > 1)
                                        "Booked (${habitWithStatus.bookedTasksThisPeriod})"
                                    else "Booked"
                                } else "Not Booked",
                                active = habitWithStatus.isBookedThisPeriod,
                                activeColor = habitColor
                            )
                        }
                        if (habit.trackPreviousPeriod) {
                            val periodTitle = periodNoun.replaceFirstChar { it.uppercase() }
                            StatusPill(
                                label = if (habitWithStatus.previousPeriodMet)
                                    "Last $periodTitle Done"
                                else
                                    "Last $periodTitle Missed",
                                active = habitWithStatus.previousPeriodMet,
                                activeColor = habitColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit habit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete habit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Circular checkbox / counter (long-press to decrement multi-time habits)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .then(
                        if (habitWithStatus.isCompletedToday) {
                            Modifier.background(habitColor)
                        } else if (habitWithStatus.completionsToday > 0) {
                            Modifier.background(habitColor.copy(alpha = 0.3f))
                        } else {
                            Modifier.border(2.dp, habitColor, CircleShape)
                        }
                    )
                    .pointerInput(habitWithStatus.isCompletedToday, habitWithStatus.completionsToday) {
                        detectTapGestures(
                            onTap = { onToggle() },
                            onLongPress = { onDecrement() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (habitWithStatus.isCompletedToday) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (habitWithStatus.dailyTarget > 1 && habitWithStatus.completionsToday > 0) {
                    Text(
                        text = "${habitWithStatus.completionsToday}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitLogDialog(
    habitWithStatus: HabitWithStatus,
    viewModel: HabitListViewModel,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var recentLogs by remember { mutableStateOf<List<HabitCompletionEntity>>(emptyList()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()) }

    LaunchedEffect(habitWithStatus.habit.id) {
        recentLogs = viewModel.getRecentLogs(habitWithStatus.habit.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(habitWithStatus.habit.icon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = habitWithStatus.habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Add a note (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (recentLogs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Previous Logs",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentLogs.forEach { log ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = dateFormat.format(Date(log.completedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!log.notes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = log.notes,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.completeWithNotes(habitWithStatus.habit.id, noteText.ifBlank { null })
                onDismiss()
            }) {
                Text("Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    activeColor: Color
) {
    val bg = if (active) activeColor.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = if (active) activeColor
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun WeeklyDots(
    completionsThisWeek: Int,
    target: Int,
    color: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(target.coerceAtMost(7)) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < completionsThisWeek) color
                        else color.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

@Composable
private fun SelfCareRoutineCard(
    routineType: String,
    cardData: SelfCareCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (routineType) {
        "morning" -> "Morning Routine"
        "medication" -> "Medication"
        "housework" -> "Housework"
        else -> "Bedtime Routine"
    }
    val icon = when (routineType) {
        "morning" -> "\u2600\uFE0F"
        "medication" -> "\uD83D\uDC8A"
        "housework" -> "\uD83C\uDFE0"
        else -> "\uD83C\uDF19"
    }
    val color = when (routineType) {
        "morning" -> Color(0xFFF59E0B)
        "medication" -> Color(0xFFEF4444)
        "housework" -> Color(0xFF10B981)
        else -> Color(0xFF8B5CF6)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cardData.isComplete) color.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (routineType != "medication") {
                    Text(
                        text = cardData.tierLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (cardData.isComplete) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${cardData.completedCount}/${cardData.totalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltInHabitCard(
    type: String,
    habitWithStatus: HabitWithStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (type) {
        "school" -> "Schoolwork"
        "leisure" -> "Leisure"
        else -> type.replaceFirstChar { it.uppercase() }
    }
    val icon = when (type) {
        "school" -> "\uD83C\uDF93"
        "leisure" -> "\uD83C\uDFB5"
        else -> "\u2B50"
    }
    val color = when (type) {
        "school" -> Color(0xFFCFB87C)
        "leisure" -> Color(0xFF8B5CF6)
        else -> Color(0xFF4A90D9)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (habitWithStatus.isCompletedToday) color.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (habitWithStatus.currentStreak > 0) {
                    StreakBadge(streak = habitWithStatus.currentStreak)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (habitWithStatus.isCompletedToday) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}
