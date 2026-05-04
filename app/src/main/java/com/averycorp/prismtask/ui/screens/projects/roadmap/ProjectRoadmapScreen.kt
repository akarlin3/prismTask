package com.averycorp.prismtask.ui.screens.projects.roadmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ExternalAnchorRepository
import com.averycorp.prismtask.domain.model.ExternalAnchor
import com.averycorp.prismtask.domain.model.RiskLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Roadmap surface (PrismTask-timeline-class scope, audit § P10 option (b))
 * — now writable. Renders phases-with-tasks, the project's unphased
 * tasks, the risk register, external anchors, and the dependency edge
 * set, with edit/delete affordances on each row and an "Add" action
 * per section. Editor dialogs live in [ProjectRoadmapEditDialogs] and
 * delegate writes to the underlying repos via [ProjectRoadmapViewModel].
 *
 * Naming note: distinct from `ui.screens.timeline.TimelineScreen` (the
 * daily time-block view) per the audit's naming-collision flag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectRoadmapScreen(
    navController: NavController,
    viewModel: ProjectRoadmapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.project?.name ?: "Project Roadmap") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.project == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("Project not found") }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(
                    title = "Phases (${state.phases.size})",
                    onAdd = { viewModel.openEditor(RoadmapEditor.PhaseEditor()) }
                )
            }
            if (state.phases.isEmpty()) {
                item { EmptySection("No phases yet — tap + to add one.") }
            } else {
                items(state.phases.size) { i ->
                    val pwt = state.phases[i]
                    PhaseCard(
                        phase = pwt.phase,
                        tasks = pwt.tasks,
                        onEdit = { viewModel.openEditor(RoadmapEditor.PhaseEditor(pwt.phase)) },
                        onDelete = { viewModel.deletePhase(pwt.phase) }
                    )
                }
            }

            if (state.unphasedTasks.isNotEmpty()) {
                item { SectionHeader(title = "Unphased Tasks (${state.unphasedTasks.size})") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            state.unphasedTasks.forEach { task ->
                                TaskRow(task)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Risks (${state.risks.size})",
                    onAdd = { viewModel.openEditor(RoadmapEditor.RiskEditor()) }
                )
            }
            if (state.risks.isEmpty()) {
                item { EmptySection("No risks logged.") }
            } else {
                items(state.risks.size) { i ->
                    val risk = state.risks[i]
                    RiskRow(
                        risk = risk,
                        onEdit = { viewModel.openEditor(RoadmapEditor.RiskEditor(risk)) },
                        onDelete = { viewModel.deleteRisk(risk) }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "External Anchors (${state.anchors.size})",
                    onAdd = { viewModel.openEditor(RoadmapEditor.AnchorEditor()) }
                )
            }
            if (state.anchors.isEmpty()) {
                item { EmptySection("No external anchors.") }
            } else {
                items(state.anchors.size) { i ->
                    val decoded = state.anchors[i]
                    AnchorRow(
                        decoded = decoded,
                        onEdit = {
                            viewModel.openEditor(
                                RoadmapEditor.AnchorEditor(decoded.entity, decoded.anchor)
                            )
                        },
                        onDelete = { viewModel.deleteAnchor(decoded.entity) }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Dependencies (${state.dependencies.size})",
                    onAdd = { viewModel.openEditor(RoadmapEditor.DependencyEditor) }
                )
            }
            if (state.dependencies.isEmpty()) {
                item { EmptySection("No task dependencies in this project.") }
            } else {
                items(state.dependencies.size) { i ->
                    val edge = state.dependencies[i]
                    DependencyRow(
                        edge = edge,
                        projectTasks = state.projectTasks,
                        onDelete = { viewModel.deleteDependency(edge) }
                    )
                }
            }
        }
    }

    when (val ed = editor) {
        is RoadmapEditor.PhaseEditor -> PhaseEditDialog(
            existing = ed.existing,
            onDismiss = viewModel::closeEditor,
            onSave = { title, description, startDate, endDate, versionAnchor ->
                viewModel.savePhase(ed.existing, title, description, startDate, endDate, versionAnchor)
            }
        )
        is RoadmapEditor.RiskEditor -> RiskEditDialog(
            existing = ed.existing,
            onDismiss = viewModel::closeEditor,
            onSave = { title, level, mitigation ->
                viewModel.saveRisk(ed.existing, title, level, mitigation)
            }
        )
        is RoadmapEditor.AnchorEditor -> AnchorEditDialog(
            existing = ed.existing,
            decoded = ed.decoded,
            onDismiss = viewModel::closeEditor,
            onSave = { label, anchor -> viewModel.saveAnchor(ed.existing, label, anchor) }
        )
        is RoadmapEditor.DependencyEditor -> DependencyAddDialog(
            projectTasks = state.projectTasks,
            onDismiss = viewModel::closeEditor,
            onSave = { blockerId, blockedId -> viewModel.addDependency(blockerId, blockedId) }
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PhaseCard(
    phase: ProjectPhaseEntity,
    tasks: List<TaskEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = phase.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                phase.versionAnchor?.takeIf { it.isNotBlank() }?.let { va ->
                    AssistChip(
                        onClick = {},
                        label = { Text(va) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit phase")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete phase")
                }
            }
            val dateRange = listOfNotNull(
                phase.startDate?.let { df.format(Date(it)) },
                phase.endDate?.let { df.format(Date(it)) }
            ).joinToString(" → ")
            if (dateRange.isNotEmpty()) {
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            phase.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks in this phase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                tasks.forEach { task -> TaskRow(task) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity) {
    val fraction = task.progressPercent?.coerceIn(0, 100)?.div(100f)
        ?: if (task.isCompleted) 1f else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(80.dp)) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp).width(36.dp)
        )
    }
}

@Composable
private fun RiskRow(
    risk: ProjectRiskEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val level = RiskLevel.fromStorage(risk.level)
    val (label, color) = when (level) {
        RiskLevel.LOW -> "LOW" to MaterialTheme.colorScheme.tertiary
        RiskLevel.MEDIUM -> "MED" to MaterialTheme.colorScheme.secondary
        RiskLevel.HIGH -> "HIGH" to MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = risk.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (risk.resolvedAt != null) TextDecoration.LineThrough else null
                )
                risk.mitigation?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AssistChip(onClick = {}, label = { Text(label) })
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit risk")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete risk")
            }
        }
    }
}

@Composable
private fun AnchorRow(
    decoded: ExternalAnchorRepository.Decoded,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val (typeLabel, body) = when (val a = decoded.anchor) {
        is ExternalAnchor.CalendarDeadline -> "DATE" to df.format(Date(a.epochMs))
        is ExternalAnchor.NumericThreshold -> "METRIC" to "${a.metric} ${a.op.symbol} ${a.value}"
        is ExternalAnchor.BooleanGate -> "GATE" to "${a.gateKey} = ${a.expectedState}"
        null -> "?" to "(unsupported anchor type)"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = {}, label = { Text(typeLabel) })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = decoded.entity.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit anchor")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete anchor")
            }
        }
    }
}

@Composable
private fun DependencyRow(
    edge: TaskDependencyEntity,
    projectTasks: List<TaskEntity>,
    onDelete: () -> Unit
) {
    val blocker = projectTasks.firstOrNull { it.id == edge.blockerTaskId }?.title
        ?: "task #${edge.blockerTaskId}"
    val blocked = projectTasks.firstOrNull { it.id == edge.blockedTaskId }?.title
        ?: "task #${edge.blockedTaskId}"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$blocker  →  $blocked",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Blocker must finish before blocked starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete dependency")
            }
        }
    }
}
