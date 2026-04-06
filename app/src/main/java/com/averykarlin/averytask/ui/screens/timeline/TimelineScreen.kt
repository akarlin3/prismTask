package com.averykarlin.averytask.ui.screens.timeline

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.ui.navigation.AveryTaskRoute
import com.averykarlin.averytask.ui.theme.LocalPriorityColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val HOUR_HEIGHT = 60.dp
private const val START_HOUR = 6
private const val END_HOUR = 23

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    navController: NavController,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val scheduledBlocks by viewModel.scheduledBlocks.collectAsStateWithLifecycle()
    val unscheduledTasks by viewModel.unscheduledTasks.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    var scheduleDialogTask by remember { mutableStateOf<TaskEntity?>(null) }

    // Scroll to current hour on first load
    LaunchedEffect(Unit) {
        val nowHour = LocalTime.now().hour
        val scrollTarget = ((nowHour - START_HOUR).coerceAtLeast(0) * HOUR_HEIGHT.value).toInt()
        scrollState.scrollTo((scrollTarget * 2.5f).toInt()) // density approximation
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.onPreviousDay() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous day", Modifier.size(20.dp))
                        }
                        Text(
                            text = currentDate.format(dateFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.onNextDay() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next day", Modifier.size(20.dp))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onGoToToday() }) {
                        Icon(Icons.Default.Today, "Go to today")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary
            val scheduledMinutes = scheduledBlocks.sumOf { ((it.endTime - it.startTime) / 60000).toInt() }
            val scheduledHours = scheduledMinutes / 60f
            Text(
                text = "${scheduledBlocks.size} scheduled · ${String.format("%.1f", scheduledHours)} hrs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Timeline area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                val totalHours = END_HOUR - START_HOUR
                val gridColor = MaterialTheme.colorScheme.outlineVariant

                // Hour grid
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (hour in START_HOUR..END_HOUR) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HOUR_HEIGHT)
                                .drawBehind {
                                    drawLine(gridColor, Offset(60.dp.toPx(), 0f), Offset(size.width, 0f), strokeWidth = 0.5f)
                                },
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = if (hour <= 12) "${hour} AM" else "${hour - 12} PM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .width(56.dp)
                                    .padding(end = 4.dp, top = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Current time indicator
                if (currentDate == today) {
                    val now = LocalTime.now()
                    val minutesSinceStart = (now.hour - START_HOUR) * 60 + now.minute
                    if (minutesSinceStart >= 0) {
                        val yOffset = (minutesSinceStart.toFloat() / 60f * HOUR_HEIGHT.value).dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = yOffset)
                                .padding(start = 52.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.Red.copy(alpha = 0.7f))
                            )
                            Text(
                                "NOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                fontSize = 8.sp,
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                            )
                        }
                    }
                }

                // Scheduled blocks
                val zone = ZoneId.systemDefault()
                val dayStart = currentDate.atTime(START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

                scheduledBlocks.forEach { block ->
                    val minutesFromStart = ((block.startTime - dayStart) / 60000f).coerceAtLeast(0f)
                    val durationMinutes = ((block.endTime - block.startTime) / 60000f).coerceAtLeast(15f)
                    val yOffset = (minutesFromStart / 60f * HOUR_HEIGHT.value).dp
                    val blockHeight = (durationMinutes / 60f * HOUR_HEIGHT.value).dp

                    Card(
                        modifier = Modifier
                            .padding(start = 60.dp, end = 8.dp)
                            .offset(y = yOffset)
                            .fillMaxWidth()
                            .height(blockHeight)
                            .clickable {
                                block.taskId?.let { navController.navigate(AveryTaskRoute.AddEditTask.createRoute(it)) }
                            },
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = LocalPriorityColors.current.forLevel(block.priority).copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(blockHeight - 8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(LocalPriorityColors.current.forLevel(block.priority))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = block.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Unscheduled section
            if (unscheduledTasks.isNotEmpty()) {
                Text(
                    text = "Unscheduled (${unscheduledTasks.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    unscheduledTasks.take(5).forEach { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scheduleDialogTask = task },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                                            .background(LocalPriorityColors.current.forLevel(task.priority))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Schedule",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Schedule dialog
    scheduleDialogTask?.let { task ->
        val timePickerState = rememberTimePickerState(
            initialHour = LocalTime.now().hour,
            initialMinute = (LocalTime.now().minute / 15) * 15
        )
        AlertDialog(
            onDismissRequest = { scheduleDialogTask = null },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = currentDate.atTime(timePickerState.hour, timePickerState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    viewModel.onScheduleTask(task.id, cal.timeInMillis)
                    scheduleDialogTask = null
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { scheduleDialogTask = null }) { Text("Cancel") }
            },
            title = { Text("Schedule: ${task.title}") },
            text = { TimePicker(state = timePickerState) }
        )
    }
}
