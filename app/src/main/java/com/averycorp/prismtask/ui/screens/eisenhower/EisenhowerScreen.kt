package com.averycorp.prismtask.ui.screens.eisenhower

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute

private val Q1_COLOR = Color(0xFFEF4444) // Red
private val Q2_COLOR = Color(0xFF3B82F6) // Blue
private val Q3_COLOR = Color(0xFFF59E0B) // Yellow/Amber
private val Q4_COLOR = Color(0xFF6B7280) // Gray

private data class QuadrantInfo(
    val key: String,
    val title: String,
    val subtitle: String,
    val color: Color
)

private val QUADRANTS = listOf(
    QuadrantInfo("Q1", "Do First", "Urgent + Important", Q1_COLOR),
    QuadrantInfo("Q2", "Schedule", "Not Urgent + Important", Q2_COLOR),
    QuadrantInfo("Q3", "Delegate", "Urgent + Not Important", Q3_COLOR),
    QuadrantInfo("Q4", "Eliminate", "Not Urgent + Not Important", Q4_COLOR)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EisenhowerScreen(
    navController: NavController,
    viewModel: EisenhowerViewModel = hiltViewModel()
) {
    val quadrants by viewModel.quadrants.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastCategorizedAt by viewModel.lastCategorizedAt.collectAsState()
    val error by viewModel.error.collectAsState()
    val expandedQuadrant by viewModel.expandedQuadrant.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eisenhower Matrix") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { viewModel.categorize() }) {
                            Icon(
                                if (lastCategorizedAt != null) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (lastCategorizedAt != null) "Re-Categorize" else "Categorize")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (expandedQuadrant != null) {
            // Full-screen view of a single quadrant
            val info = QUADRANTS.find { it.key == expandedQuadrant } ?: return@Scaffold
            val tasks = quadrants[expandedQuadrant] ?: emptyList()
            ExpandedQuadrantView(
                info = info,
                tasks = tasks,
                onBack = { viewModel.expandQuadrant(null) },
                onTaskClick = { taskId ->
                    navController.navigate(PrismTaskRoute.AddEditTask.createRoute(taskId))
                },
                onCompleteTask = { viewModel.completeTask(it) },
                onMoveTask = { taskId, quadrant -> viewModel.moveTaskToQuadrant(taskId, quadrant) },
                modifier = Modifier.padding(padding)
            )
        } else {
            // 2x2 grid view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Top row: Q1 and Q2
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuadrantCell(
                        info = QUADRANTS[0],
                        tasks = quadrants["Q1"] ?: emptyList(),
                        isLoading = isLoading,
                        onHeaderClick = { viewModel.expandQuadrant("Q1") },
                        onTaskClick = { taskId ->
                            navController.navigate(PrismTaskRoute.AddEditTask.createRoute(taskId))
                        },
                        onCompleteTask = { viewModel.completeTask(it) },
                        onMoveTask = { taskId, quadrant -> viewModel.moveTaskToQuadrant(taskId, quadrant) },
                        modifier = Modifier.weight(1f)
                    )
                    QuadrantCell(
                        info = QUADRANTS[1],
                        tasks = quadrants["Q2"] ?: emptyList(),
                        isLoading = isLoading,
                        onHeaderClick = { viewModel.expandQuadrant("Q2") },
                        onTaskClick = { taskId ->
                            navController.navigate(PrismTaskRoute.AddEditTask.createRoute(taskId))
                        },
                        onCompleteTask = { viewModel.completeTask(it) },
                        onMoveTask = { taskId, quadrant -> viewModel.moveTaskToQuadrant(taskId, quadrant) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Bottom row: Q3 and Q4
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuadrantCell(
                        info = QUADRANTS[2],
                        tasks = quadrants["Q3"] ?: emptyList(),
                        isLoading = isLoading,
                        onHeaderClick = { viewModel.expandQuadrant("Q3") },
                        onTaskClick = { taskId ->
                            navController.navigate(PrismTaskRoute.AddEditTask.createRoute(taskId))
                        },
                        onCompleteTask = { viewModel.completeTask(it) },
                        onMoveTask = { taskId, quadrant -> viewModel.moveTaskToQuadrant(taskId, quadrant) },
                        modifier = Modifier.weight(1f)
                    )
                    QuadrantCell(
                        info = QUADRANTS[3],
                        tasks = quadrants["Q4"] ?: emptyList(),
                        isLoading = isLoading,
                        onHeaderClick = { viewModel.expandQuadrant("Q4") },
                        onTaskClick = { taskId ->
                            navController.navigate(PrismTaskRoute.AddEditTask.createRoute(taskId))
                        },
                        onCompleteTask = { viewModel.completeTask(it) },
                        onMoveTask = { taskId, quadrant -> viewModel.moveTaskToQuadrant(taskId, quadrant) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    info: QuadrantInfo,
    tasks: List<TaskEntity>,
    isLoading: Boolean,
    onHeaderClick: () -> Unit,
    onTaskClick: (Long) -> Unit,
    onCompleteTask: (Long) -> Unit,
    onMoveTask: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = info.color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(info.color)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = info.color
                    )
                    Text(
                        text = info.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
                Badge(
                    containerColor = info.color.copy(alpha = 0.2f),
                    contentColor = info.color
                ) {
                    Text(tasks.size.toString(), fontSize = 10.sp)
                }
            }

            // Task list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = info.color
                    )
                }
            } else if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No Tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        CompactTaskCard(
                            task = task,
                            quadrantColor = info.color,
                            onClick = { onTaskClick(task.id) },
                            onComplete = { onCompleteTask(task.id) },
                            onMoveTask = { quadrant -> onMoveTask(task.id, quadrant) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTaskCard(
    task: TaskEntity,
    quadrantColor: Color,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onMoveTask: (String) -> Unit
) {
    var showMoveMenu by remember { mutableStateOf(false) }
    var showReasonDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority dot
            val priorityColor = when (task.priority) {
                4 -> Color(0xFFEF4444)
                3 -> Color(0xFFF59E0B)
                2 -> Color(0xFF3B82F6)
                1 -> Color(0xFF22C55E)
                else -> Color(0xFF9CA3AF)
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(priorityColor)
            )
            Spacer(Modifier.width(6.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Due date indicator
            if (task.dueDate != null) {
                val daysUntil = ((task.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                val dateColor = when {
                    daysUntil < 0 -> Color(0xFFEF4444)
                    daysUntil == 0 -> Color(0xFFF59E0B)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = when {
                        daysUntil < 0 -> "${-daysUntil}d ago"
                        daysUntil == 0 -> "Today"
                        daysUntil == 1 -> "Tmrw"
                        else -> "${daysUntil}d"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = dateColor,
                    fontSize = 9.sp
                )
            }

            // Info button for AI reasoning
            if (task.eisenhowerReason != null) {
                IconButton(
                    onClick = { showReasonDialog = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "AI Reasoning",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // Long-press move menu (using dropdown on click for simplicity)
    Box {
        DropdownMenu(
            expanded = showMoveMenu,
            onDismissRequest = { showMoveMenu = false }
        ) {
            QUADRANTS.forEach { q ->
                if (q.key != task.eisenhowerQuadrant) {
                    DropdownMenuItem(
                        text = { Text("Move to ${q.key} (${q.title})") },
                        onClick = {
                            onMoveTask(q.key)
                            showMoveMenu = false
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(q.color)
                            )
                        }
                    )
                }
            }
        }
    }

    // AI reasoning dialog
    if (showReasonDialog && task.eisenhowerReason != null) {
        AlertDialog(
            onDismissRequest = { showReasonDialog = false },
            title = { Text("AI Reasoning") },
            text = { Text(task.eisenhowerReason) },
            confirmButton = {
                TextButton(onClick = { showReasonDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ExpandedQuadrantView(
    info: QuadrantInfo,
    tasks: List<TaskEntity>,
    onBack: () -> Unit,
    onTaskClick: (Long) -> Unit,
    onCompleteTask: (Long) -> Unit,
    onMoveTask: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Grid")
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(info.color)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "${info.key}: ${info.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = info.color
                )
                Text(
                    info.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Badge(
                containerColor = info.color.copy(alpha = 0.2f),
                contentColor = info.color
            ) {
                Text("${tasks.size} tasks")
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No Tasks in This Quadrant",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    CompactTaskCard(
                        task = task,
                        quadrantColor = info.color,
                        onClick = { onTaskClick(task.id) },
                        onComplete = { onCompleteTask(task.id) },
                        onMoveTask = { quadrant -> onMoveTask(task.id, quadrant) }
                    )
                }
            }
        }
    }
}
