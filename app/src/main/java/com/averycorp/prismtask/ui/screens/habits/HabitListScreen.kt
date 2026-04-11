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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberDatePickerState
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
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
    var bookingHabit by remember { mutableStateOf<HabitWithStatus?>(null) }
    var activityLogHabit by remember { mutableStateOf<HabitWithStatus?>(null) }
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
            TopAppBar(
                title = { Text("Habits", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
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
                    icon = "\u2728",
                    title = "Start Building Habits",
                    description = "Track daily routines at your own pace.",
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
                                if (listItem.habitWithStatus.habit.isBookable) {
                                    BookableHabitItem(
                                        habitWithStatus = listItem.habitWithStatus,
                                        onClick = {
                                            navController.navigate(
                                                PrismTaskRoute.HabitDetail.createRoute(listItem.habitWithStatus.habit.id)
                                            )
                                        },
                                        onBook = { bookingHabit = listItem.habitWithStatus },
                                        onLog = { activityLogHabit = listItem.habitWithStatus },
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
                                } else {
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

    bookingHabit?.let { hws ->
        BookingDialog(
            habitWithStatus = hws,
            onConfirm = { date, note ->
                viewModel.onSetBooked(hws.habit.id, true, date, note)
                bookingHabit = null
            },
            onUnbook = {
                viewModel.onSetBooked(hws.habit.id, false, null, null)
                bookingHabit = null
            },
            onDismiss = { bookingHabit = null }
        )
    }

    activityLogHabit?.let { hws ->
        ActivityLogDialog(
            habitWithStatus = hws,
            onConfirm = { date, notes ->
                viewModel.onLogActivity(hws.habit.id, date, notes)
                activityLogHabit = null
            },
            onDismiss = { activityLogHabit = null }
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
                    if (habit.frequencyPeriod == "daily" && habitWithStatus.dailyTarget > 1) {
                        Text(
                            text = "${habitWithStatus.completionsToday}/${habitWithStatus.dailyTarget} today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (habit.showStreak && habitWithStatus.currentStreak > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            StreakBadge(streak = habitWithStatus.currentStreak)
                        }
                    } else if (habit.showStreak && habitWithStatus.currentStreak > 0) {
                        StreakBadge(streak = habitWithStatus.currentStreak)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${habitWithStatus.completionsThisWeek} days this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "${habitWithStatus.completionsThisWeek} done $periodLabel",
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
private fun BookableHabitItem(
    habitWithStatus: HabitWithStatus,
    onClick: () -> Unit,
    onBook: () -> Unit,
    onLog: () -> Unit,
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
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

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

            // Name + status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Booking status line
                if (habit.isBooked) {
                    val bookedDateStr = habit.bookedDate?.let { dateFormat.format(Date(it)) } ?: ""
                    val noteStr = habit.bookedNote?.let { " \u2014 $it" } ?: ""
                    Text(
                        text = "\uD83D\uDCC5 Booked: $bookedDateStr$noteStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "\u23F3 Not Booked",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B)
                    )
                }

                // Last done line
                if (habitWithStatus.lastLogDate != null) {
                    val daysAgo = TimeUnit.MILLISECONDS.toDays(
                        System.currentTimeMillis() - habitWithStatus.lastLogDate
                    )
                    val lastDateStr = dateFormat.format(Date(habitWithStatus.lastLogDate))
                    Text(
                        text = "Last done: $lastDateStr ($daysAgo days ago)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No activities logged yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            // Action buttons column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Book button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (habit.isBooked) habitColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .clickable { onBook() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\uD83D\uDCC5", style = MaterialTheme.typography.labelLarge)
                }
                // Log button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onLog() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u2705", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookingDialog(
    habitWithStatus: HabitWithStatus,
    onConfirm: (Long, String?) -> Unit,
    onUnbook: () -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    var bookedDate by remember { mutableStateOf(habitWithStatus.habit.bookedDate ?: today) }
    var bookedNote by remember { mutableStateOf(habitWithStatus.habit.bookedNote ?: "") }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val tomorrow = remember {
        val cal = Calendar.getInstance()
        cal.timeInMillis = today
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.timeInMillis
    }
    val nextWeek = remember {
        val cal = Calendar.getInstance()
        cal.timeInMillis = today
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        cal.timeInMillis
    }
    val presetDates = remember { setOf(today, tomorrow, nextWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(habitWithStatus.habit.icon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Book ${habitWithStatus.habit.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Date selector buttons
                Text("Date", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today" to today, "Tomorrow" to tomorrow, "Next Week" to nextWeek).forEach { (label, date) ->
                        FilterChip(
                            selected = bookedDate == date,
                            onClick = { bookedDate = date },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                FilterChip(
                    selected = bookedDate !in presetDates,
                    onClick = { showDatePicker = true },
                    label = { Text("Pick Date\u2026", style = MaterialTheme.typography.labelSmall) }
                )
                Text(
                    text = "Selected: ${dateFormat.format(Date(bookedDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = bookedNote,
                    onValueChange = { bookedNote = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Dr. Smith, 2pm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(bookedDate, bookedNote.ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (habitWithStatus.habit.isBooked) {
                    TextButton(onClick = onUnbook) {
                        Text("Unbook", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDayStartToUtcMillis(bookedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { bookedDate = utcMillisToLocalDayStart(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Material 3 DatePicker returns UTC midnight millis for the selected date. Convert that to
 * midnight in the system default timezone so callers storing it as "day start" get the same
 * calendar day the user tapped — otherwise users in timezones behind UTC see the previous day.
 */
private fun utcMillisToLocalDayStart(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = utcMillis
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)
    val localCal = Calendar.getInstance()
    localCal.set(year, month, day, 0, 0, 0)
    localCal.set(Calendar.MILLISECOND, 0)
    return localCal.timeInMillis
}

/**
 * Inverse of [utcMillisToLocalDayStart]: converts a local-midnight millis value into the
 * UTC-midnight millis that Material 3 DatePicker expects for pre-selection.
 */
private fun localDayStartToUtcMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance()
    localCal.timeInMillis = localMillis
    val year = localCal.get(Calendar.YEAR)
    val month = localCal.get(Calendar.MONTH)
    val day = localCal.get(Calendar.DAY_OF_MONTH)
    val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    utcCal.set(year, month, day, 0, 0, 0)
    utcCal.set(Calendar.MILLISECOND, 0)
    return utcCal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityLogDialog(
    habitWithStatus: HabitWithStatus,
    onConfirm: (Long, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    var logDate by remember { mutableStateOf(today) }
    var logNotes by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val yesterday = remember {
        val cal = Calendar.getInstance()
        cal.timeInMillis = today
        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.timeInMillis
    }
    val presetDates = remember { setOf(today, yesterday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(habitWithStatus.habit.icon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log ${habitWithStatus.habit.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Date", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today" to today, "Yesterday" to yesterday).forEach { (label, date) ->
                        FilterChip(
                            selected = logDate == date,
                            onClick = { logDate = date },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    FilterChip(
                        selected = logDate !in presetDates,
                        onClick = { showDatePicker = true },
                        label = { Text("Pick Date\u2026", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                Text(
                    text = "Selected: ${dateFormat.format(Date(logDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = logNotes,
                    onValueChange = { logNotes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Went to dentist, all good") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(logDate, logNotes.ifBlank { null }) }) {
                Text("Log Activity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = logDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { logDate = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
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
                if (habitWithStatus.habit.showStreak && habitWithStatus.currentStreak > 0) {
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
