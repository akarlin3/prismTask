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
import androidx.compose.material.icons.filled.Circle
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.ExternalAnchorRepository
import com.averycorp.prismtask.domain.model.ExternalAnchor
import com.averycorp.prismtask.domain.model.RiskLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only roadmap surface (audit § P10 option (b), per O3 override).
 *
 * Renders phases-with-tasks (with each task's fractional progress
 * bar), the project's unphased tasks, the risk register (color-coded
 * by [RiskLevel]), and external anchors with variant-aware bodies for
 * [ExternalAnchor.CalendarDeadline], [ExternalAnchor.NumericThreshold],
 * and [ExternalAnchor.BooleanGate].
 *
 * Naming note: distinct from [com.averycorp.prismtask.ui.screens.timeline.TimelineScreen]
 * (the daily time-block view) per the audit's naming-collision flag —
 * "Roadmap" stays reserved for the phase-Gantt surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectRoadmapScreen(
    navController: NavController,
    viewModel: ProjectRoadmapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        }
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
            item { SectionHeader("Phases (${state.phases.size})") }
            if (state.phases.isEmpty()) {
                item { EmptySection("No phases yet — add one from the Project edit screen.") }
            } else {
                items(state.phases.size) { i ->
                    val pwt = state.phases[i]
                    PhaseCard(phase = pwt.phase, tasks = pwt.tasks)
                }
            }

            if (state.unphasedTasks.isNotEmpty()) {
                item { SectionHeader("Unphased Tasks (${state.unphasedTasks.size})") }
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

            item { SectionHeader("Risks (${state.risks.size})") }
            if (state.risks.isEmpty()) {
                item { EmptySection("No risks logged.") }
            } else {
                items(state.risks.size) { i -> RiskRow(state.risks[i]) }
            }

            item { SectionHeader("External Anchors (${state.anchors.size})") }
            if (state.anchors.isEmpty()) {
                item { EmptySection("No external anchors.") }
            } else {
                items(state.anchors.size) { i -> AnchorRow(state.anchors[i]) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
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
private fun PhaseCard(phase: ProjectPhaseEntity, tasks: List<TaskEntity>) {
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
    // Mirrors the burndown's `progress_percent ?: (DONE ? 100 : 0)`
    // semantics so the bar stays consistent with the analytics layer.
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
private fun RiskRow(risk: ProjectRiskEntity) {
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
        }
    }
}

@Composable
private fun AnchorRow(decoded: ExternalAnchorRepository.Decoded) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val (typeLabel, body) = when (val a = decoded.anchor) {
        is ExternalAnchor.CalendarDeadline -> "DATE" to df.format(Date(a.epochMs))
        // ComparisonOp carries a glyph (`<`, `<=`, `>`, `>=`, `==`) — render it
        // verbatim so the row matches how the user authored the threshold.
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
        }
    }
}
