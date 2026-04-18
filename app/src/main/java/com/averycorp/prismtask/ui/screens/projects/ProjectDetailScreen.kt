package com.averycorp.prismtask.ui.screens.projects

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.MilestoneEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.ProjectDetail
import com.averycorp.prismtask.domain.model.ProjectStatus
import com.averycorp.prismtask.ui.components.StreakBadge
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    navController: NavController,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val prismColors = LocalPrismColors.current
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    // If the project disappears (deleted externally, or never existed), pop back.
    LaunchedEffect(detail) {
        if (detail == null && viewModel.projectId != -1L) {
            // Wait one tick — the Flow emits null on the first collection before
            // the DB query resolves, so we need to differentiate between
            // "loading" and "deleted." A simple heuristic: if still null after
            // a short delay, it's gone.
            kotlinx.coroutines.delay(400)
            if (viewModel.detail.value == null) navController.popBackStack()
        }
    }

    val project = detail?.project
    Scaffold(
        containerColor = prismColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project?.name.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        color = prismColors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = prismColors.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(
                            PrismTaskRoute.AddEditProject.createRoute(viewModel.projectId)
                        )
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Project",
                            tint = prismColors.onSurface
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More Actions",
                            tint = prismColors.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        when (detail?.status) {
                            ProjectStatus.ACTIVE -> {
                                DropdownMenuItem(
                                    text = { Text("Mark Completed") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                                    onClick = {
                                        viewModel.completeProject()
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                                    onClick = {
                                        viewModel.archiveProject()
                                        menuOpen = false
                                    }
                                )
                            }
                            ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED -> {
                                DropdownMenuItem(
                                    text = { Text("Reopen") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = {
                                        viewModel.reopenProject()
                                        menuOpen = false
                                    }
                                )
                            }
                            null -> Unit
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = prismColors.urgentAccent) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = prismColors.urgentAccent
                                )
                            },
                            onClick = {
                                menuOpen = false
                                deleteDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = prismColors.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 2) { // Tasks tab
                // Opens the add-task screen; pre-filling `projectId` requires
                // either a new nav argument on AddEditTask or routing through
                // the existing AddEditTaskSheetHost. Tracking as Phase-2
                // polish — for now users pick the project on the task form.
                FloatingActionButton(
                    onClick = {
                        navController.navigate(PrismTaskRoute.AddEditTask.createRoute())
                    },
                    containerColor = prismColors.primary,
                    contentColor = prismColors.onBackground
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = prismColors.surface,
                contentColor = prismColors.primary
            ) {
                listOf("Overview", "Milestones", "Tasks").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewSection(detail = detail)
                1 -> MilestonesSection(
                    milestones = detail?.milestones.orEmpty(),
                    onAdd = viewModel::addMilestone,
                    onToggle = viewModel::toggleMilestone,
                    onRename = viewModel::updateMilestoneTitle,
                    onDelete = viewModel::deleteMilestone,
                    onReorder = viewModel::reorderMilestones
                )
                2 -> TasksSection(
                    tasks = tasks,
                    onTaskClick = { taskId ->
                        navController.navigate(PrismTaskRoute.AddEditTask.createRoute(taskId))
                    }
                )
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete Project?") },
            text = {
                Text(
                    "This will permanently remove the project and its milestones. " +
                        "Linked tasks will keep existing but become unassigned."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    viewModel.deleteProject { navController.popBackStack() }
                }) {
                    Text("Delete", color = prismColors.urgentAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------

@Composable
private fun OverviewSection(detail: ProjectDetail?) {
    val prismColors = LocalPrismColors.current
    if (detail == null) {
        Box(modifier = Modifier.fillMaxSize()) { /* blank while loading */ }
        return
    }

    val accent = parseHex(detail.project.themeColorKey ?: detail.project.color, prismColors.primary)
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: streak + progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StreakBadge(streak = detail.streak.resilientStreak)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${detail.completedMilestones}/${detail.totalMilestones} Milestones",
                style = MaterialTheme.typography.labelMedium,
                color = prismColors.muted
            )
        }
        LinearProgressIndicator(
            progress = { detail.milestoneProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = accent,
            trackColor = prismColors.surfaceVariant
        )

        // Description
        if (!detail.project.description.isNullOrBlank()) {
            SectionHeader("About")
            Text(
                text = detail.project.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = prismColors.onSurface
            )
        }

        // Dates
        if (detail.project.startDate != null || detail.project.endDate != null) {
            SectionHeader("Dates")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                detail.project.startDate?.let {
                    Column {
                        Text("Start", style = MaterialTheme.typography.labelSmall, color = prismColors.muted)
                        Text(dateFmt.format(it), color = prismColors.onSurface)
                    }
                }
                detail.project.endDate?.let {
                    Column {
                        Text("End", style = MaterialTheme.typography.labelSmall, color = prismColors.muted)
                        Text(dateFmt.format(it), color = prismColors.onSurface)
                    }
                }
            }
        }

        // Status + task counts
        SectionHeader("At A Glance")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("Status", style = MaterialTheme.typography.labelSmall, color = prismColors.muted)
                Text(
                    when (detail.status) {
                        ProjectStatus.ACTIVE -> "Active"
                        ProjectStatus.COMPLETED -> "Completed"
                        ProjectStatus.ARCHIVED -> "Archived"
                    },
                    color = prismColors.onSurface
                )
            }
            Column {
                Text("Tasks", style = MaterialTheme.typography.labelSmall, color = prismColors.muted)
                Text("${detail.totalTasks - detail.openTasks}/${detail.totalTasks}", color = prismColors.onSurface)
            }
            Column {
                Text("Theme", style = MaterialTheme.typography.labelSmall, color = prismColors.muted)
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val prismColors = LocalPrismColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = prismColors.muted,
        fontWeight = FontWeight.SemiBold
    )
}

// ---------------------------------------------------------------------
// Milestones
// ---------------------------------------------------------------------

@Composable
private fun MilestonesSection(
    milestones: List<MilestoneEntity>,
    onAdd: (String) -> Unit,
    onToggle: (MilestoneEntity, Boolean) -> Unit,
    onRename: (MilestoneEntity, String) -> Unit,
    onDelete: (MilestoneEntity) -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    val prismColors = LocalPrismColors.current
    var draftOrder by remember(milestones) { mutableStateOf(milestones) }
    var newTitle by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val mutable = draftOrder.toMutableList()
        val fromIdx = mutable.indexOfFirst { it.id == from.key }
        val toIdx = mutable.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1) {
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            draftOrder = mutable
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Add bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("New Milestone") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        onAdd(newTitle)
                        newTitle = ""
                    }
                },
                enabled = newTitle.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add")
            }
        }

        if (draftOrder.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "No Milestones Yet.",
                color = prismColors.muted,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = draftOrder,
                    key = { it.id }
                ) { milestone ->
                    ReorderableItem(reorderState, key = milestone.id) { isDragging ->
                        MilestoneRow(
                            milestone = milestone,
                            isDragging = isDragging,
                            onToggle = { onToggle(milestone, it) },
                            onRename = { onRename(milestone, it) },
                            onDelete = { onDelete(milestone) },
                            dragHandle = {
                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    onClick = {},
                                    content = {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Reorder",
                                            tint = prismColors.muted
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Commit the reorder when the user's draft order diverges from
            // what's in the DB. The key is a hash — a new List literal on
            // every recomposition would retrigger the effect forever.
            val draftIdsHash = remember(draftOrder) { draftOrder.map { it.id }.hashCode() }
            LaunchedEffect(draftIdsHash) {
                val newOrder = draftOrder.map { it.id }
                val existingOrder = milestones.map { it.id }
                if (newOrder != existingOrder && newOrder.isNotEmpty()) {
                    onReorder(newOrder)
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(
    milestone: MilestoneEntity,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val prismColors = LocalPrismColors.current
    var editing by remember { mutableStateOf(false) }
    var draftTitle by remember(milestone.id) { mutableStateOf(milestone.title) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDragging) prismColors.surfaceVariant else prismColors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        dragHandle()
        IconButton(
            onClick = { onToggle(!milestone.isCompleted) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (milestone.isCompleted) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = if (milestone.isCompleted) "Completed" else "Not Completed",
                tint = if (milestone.isCompleted) prismColors.primary else prismColors.muted
            )
        }
        if (editing) {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                onRename(draftTitle)
                editing = false
            }) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        } else {
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.bodyMedium,
                color = prismColors.onSurface,
                textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier
                    .weight(1f)
                    .clickable { editing = true }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Milestone",
                    tint = prismColors.muted
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------

@Composable
private fun TasksSection(
    tasks: List<TaskEntity>,
    onTaskClick: (Long) -> Unit
) {
    val prismColors = LocalPrismColors.current
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "No Tasks On This Project Yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = prismColors.muted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap + To Add One.",
                style = MaterialTheme.typography.bodySmall,
                color = prismColors.muted
            )
        }
        return
    }

    // Deliberate minimal row rather than reusing TaskListItemScopes — that
    // scope requires a large bag of collaborators (tags, attachments,
    // subtasks, viewmodel). For Phase 2 we keep the detail screen's task
    // list lightweight; deeper integration can come with Phase 2 polish.
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(tasks, key = { it.id }) { task ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(prismColors.surface)
                    .clickable { onTaskClick(task.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (task.isCompleted) prismColors.primary else prismColors.muted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = prismColors.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun parseHex(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
