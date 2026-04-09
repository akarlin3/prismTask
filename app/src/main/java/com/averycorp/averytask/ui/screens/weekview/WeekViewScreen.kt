package com.averycorp.averytask.ui.screens.weekview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.ui.screens.addedittask.AddEditTaskSheetHost
import com.averycorp.averytask.ui.theme.LocalPriorityColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class WeekTaskEditorState(
    val taskId: Long? = null,
    val initialDate: Long? = null,
)

private val OverdueRed = Color(0xFFD93025)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    navController: NavController,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val weekDays by viewModel.weekDays.collectAsStateWithLifecycle()
    val weekTasks by viewModel.weekTasks.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    var editorState by remember { mutableStateOf<WeekTaskEditorState?>(null) }

    val weekEnd = weekStart.plusDays(6)
    val headerFormatter = DateTimeFormatter.ofPattern("MMM d")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.onPreviousWeek() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = "${weekStart.format(headerFormatter)} – ${weekEnd.format(headerFormatter)}, ${weekStart.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.onNextWeek() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onGoToToday() }) {
                        Icon(Icons.Default.Today, "Go to today")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 4.dp)
        ) {
            weekDays.forEach { date ->
                val isToday = date == today
                val isPast = date.isBefore(today)
                val isWeekend = date.dayOfWeek.value >= 6
                val tasks = weekTasks[date].orEmpty()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isToday) Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            ) else Modifier
                        )
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Day header
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .then(
                                if (isToday) Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (isPast) 0.4f else 1f
                            )
                        )
                    }

                    if (tasks.isNotEmpty()) {
                        Text(
                            text = "${tasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Task cards
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val displayTasks = if (tasks.size > 4) tasks.take(4) else tasks
                        items(displayTasks, key = { it.id }) { task ->
                            WeekTaskCard(
                                task = task,
                                isOverdue = isPast && !task.isCompleted,
                                onClick = { editorState = WeekTaskEditorState(taskId = task.id) }
                            )
                        }
                        if (tasks.size > 4) {
                            item {
                                Text(
                                    text = "+${tasks.size - 4} more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Add button
                    IconButton(
                        onClick = {
                            val dayStartMillis = date.atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            editorState = WeekTaskEditorState(initialDate = dayStartMillis)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add task",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    editorState?.let { state ->
        AddEditTaskSheetHost(
            taskId = state.taskId,
            projectId = null,
            initialDate = state.initialDate,
            onDismiss = { editorState = null }
        )
    }
}

@Composable
private fun WeekTaskCard(task: TaskEntity, isOverdue: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                OverdueRed.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            if (task.priority > 0) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LocalPriorityColors.current.forLevel(task.priority))
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp
            )
        }
    }
}
