package com.averycorp.prismtask.ui.screens.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.averycorp.prismtask.data.remote.api.AmbiguousEntityHintResponse
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.domain.model.BatchMutationType

/**
 * Full-screen preview of an AI-parsed batch command. Renders one row per
 * proposed mutation with a checkbox so the user can opt out individual
 * rows before tapping Approve.
 *
 * Returns to the caller via [onApproved] (with the freshly minted
 * `batch_id` so the caller can show an "Undo" Snackbar) or [onCancelled].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPreviewScreen(
    navController: NavHostController,
    commandText: String,
    onApproved: (batchId: String, appliedCount: Int, skippedCount: Int) -> Unit,
    onCancelled: () -> Unit,
    viewModel: BatchPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()

    LaunchedEffect(commandText) {
        viewModel.loadPreview(commandText)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BatchEvent.Approved -> onApproved(event.batchId, event.appliedCount, event.skippedCount)
                is BatchEvent.Cancelled -> onCancelled()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch preview") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancel() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        },
        bottomBar = {
            BatchPreviewBottomBar(
                state = state,
                onCancel = viewModel::cancel,
                onApprove = viewModel::approve
            )
        }
    ) { padding ->
        when (val s = state) {
            BatchPreviewState.Idle -> Box(Modifier.fillMaxSize().padding(padding))
            is BatchPreviewState.Loading -> LoadingBody(s.commandText, padding)
            is BatchPreviewState.Committing -> LoadingBody("Applying \"${s.commandText}\"…", padding)
            is BatchPreviewState.Error -> ErrorBody(s.message, padding, onRetry = {
                viewModel.loadPreview(s.commandText)
            })
            is BatchPreviewState.Loaded -> LoadedBody(
                state = s,
                excluded = excluded,
                onToggle = viewModel::toggleExclusion,
                padding = padding
            )
        }
    }
}

@Composable
private fun LoadingBody(label: String, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorBody(message: String, padding: PaddingValues, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn't parse that command", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun LoadedBody(
    state: BatchPreviewState.Loaded,
    excluded: Set<Int>,
    onToggle: (Int) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CommandSummary(state) }

        if (state.confidence < 0.7f) {
            item { ConfidenceBanner(state.confidence) }
        }

        if (state.ambiguousEntities.isNotEmpty()) {
            item { AmbiguityBanner(state.ambiguousEntities) }
        }

        if (state.mutations.isEmpty()) {
            item {
                Text(
                    "No matching changes — refine your command and try again.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        itemsIndexed(state.mutations) { idx, mutation ->
            MutationRow(
                mutation = mutation,
                currentTags = state.currentTags,
                excluded = idx in excluded,
                onToggle = { onToggle(idx) }
            )
        }
    }
}

@Composable
private fun CommandSummary(state: BatchPreviewState.Loaded) {
    val total = state.mutations.size
    val active = total - 0 // exclusion count is rendered live next to Approve
    Column {
        Text(
            "\"${state.commandText}\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "$active proposed change${if (active == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ConfidenceBanner(confidence: Float) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            "Low confidence (${(confidence * 100).toInt()}%) — review carefully before approving.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AmbiguityBanner(hints: List<AmbiguousEntityHintResponse>) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Ambiguous references", style = MaterialTheme.typography.titleSmall)
            for (h in hints) {
                Text(
                    "• \"${h.phrase}\" — ${h.note ?: "multiple matches"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MutationRow(
    mutation: ProposedMutationResponse,
    currentTags: Map<Long, List<String>>,
    excluded: Boolean,
    onToggle: () -> Unit
) {
    val color = mutationColor(mutation.mutationType)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.06f))
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(checked = !excluded, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    mutation.humanReadableDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${mutation.entityType} • ${mutation.mutationType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                if (mutation.mutationType == BatchMutationType.TAG_CHANGE.name &&
                    mutation.entityType == "TASK"
                ) {
                    val taskId = mutation.entityId.toLongOrNull()
                    val current = taskId?.let { currentTags[it] }.orEmpty()
                    val added = (mutation.proposedNewValues["tags_added"] as? List<*>)
                        ?.mapNotNull { it as? String }.orEmpty()
                    val removed = (mutation.proposedNewValues["tags_removed"] as? List<*>)
                        ?.mapNotNull { it as? String }.orEmpty()
                    TagDiffChips(current = current, added = added, removed = removed)
                }
                if (mutation.mutationType == BatchMutationType.STATE_CHANGE.name &&
                    mutation.entityType == "MEDICATION"
                ) {
                    val tier = mutation.proposedNewValues["tier"] as? String
                    val slotKey = mutation.proposedNewValues["slot_key"] as? String
                    if (tier != null) {
                        MedicationTierChip(tier = tier, slotKey = slotKey)
                    }
                }
            }
        }
    }
}

/**
 * Compact target-tier chip for a STATE_CHANGE mutation on MEDICATION.
 * The user has already seen the prior tier in the picker that sourced
 * the command — this strip just confirms what tier the slot will land
 * on after Approve. Color matches the tier verb so the row remains
 * visually consistent with the rest of the preview.
 */
@Composable
private fun MedicationTierChip(tier: String, slotKey: String?) {
    val (bg, fg) = when (tier.lowercase()) {
        "skipped" -> Color(0xFF9E9E9E).copy(alpha = 0.18f) to Color(0xFF424242)
        "essential" -> Color(0xFFE0A82E).copy(alpha = 0.18f) to Color(0xFF8C5A00)
        "prescription" -> Color(0xFF1565C0).copy(alpha = 0.18f) to Color(0xFF0D47A1)
        "complete" -> Color(0xFF2E7D32).copy(alpha = 0.18f) to Color(0xFF1B5E20)
        else ->
            MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("→ ", style = MaterialTheme.typography.labelSmall)
        Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
            Text(
                text = if (slotKey.isNullOrBlank()) tier else "$tier ($slotKey)",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Compact diff strip for a TAG_CHANGE mutation. The "From" row reflects
 * the task's current tag list (kept as-is for tags that survive); the
 * "To" row shows the same kept tags neutral, plus + chips (green) for
 * fresh additions and − chips (red) for tags being dropped. Auto-create
 * vs. existing-tag is intentionally not surfaced — matches web slice
 * #728's behavior.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagDiffChips(
    current: List<String>,
    added: List<String>,
    removed: List<String>
) {
    val currentLower = current.map { it.lowercase() }.toSet()
    val removedLower = removed.map { it.lowercase() }.toSet()
    val addedLower = added.map { it.lowercase() }.toSet()

    // Filter out removals that don't actually match any current tag — the
    // backend can speculatively emit removals; we only render the ones
    // that will land. Same with additions already present.
    val effectiveRemovals = removed.filter { it.lowercase() in currentLower }
    val effectiveAdditions = added.filter { it.lowercase() !in currentLower }
    val keptTags = current.filter { it.lowercase() !in removedLower }

    if (current.isEmpty() && effectiveAdditions.isEmpty() && effectiveRemovals.isEmpty()) {
        // Nothing to show — empty mutation.
        return
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "From: ",
                style = MaterialTheme.typography.labelSmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (current.isEmpty()) {
                    Text(
                        "(none)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (name in current) TagChip(name, kind = TagChipKind.NEUTRAL)
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "To: ",
                style = MaterialTheme.typography.labelSmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (name in keptTags) TagChip(name, kind = TagChipKind.NEUTRAL)
                for (name in effectiveAdditions) TagChip(name, kind = TagChipKind.ADDED)
                for (name in effectiveRemovals) TagChip(name, kind = TagChipKind.REMOVED)
                if (keptTags.isEmpty() && effectiveAdditions.isEmpty() && effectiveRemovals.isEmpty()) {
                    Text(
                        "(none)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private enum class TagChipKind { NEUTRAL, ADDED, REMOVED }

@Composable
private fun TagChip(name: String, kind: TagChipKind) {
    val (bg, fg) = when (kind) {
        TagChipKind.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
        TagChipKind.ADDED -> Color(0xFF2E7D32).copy(alpha = 0.15f) to Color(0xFF1B5E20)
        TagChipKind.REMOVED -> Color(0xFFC9302C).copy(alpha = 0.15f) to Color(0xFF8E1F1B)
    }
    val prefix = when (kind) {
        TagChipKind.NEUTRAL -> ""
        TagChipKind.ADDED -> "+ "
        TagChipKind.REMOVED -> "− "
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = "$prefix#$name",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BatchPreviewBottomBar(
    state: BatchPreviewState,
    onCancel: () -> Unit,
    onApprove: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                val approveEnabled = state is BatchPreviewState.Loaded && state.mutations.isNotEmpty()
                Button(
                    enabled = approveEnabled,
                    onClick = onApprove
                ) {
                    Text("Approve")
                }
            }
        }
    }
}

private fun mutationColor(mutationTypeName: String): Color = when (
    runCatching { BatchMutationType.valueOf(mutationTypeName) }.getOrNull()
) {
    BatchMutationType.RESCHEDULE -> Color(0xFFE0A82E) // amber
    BatchMutationType.DELETE -> Color(0xFFC9302C) // red
    BatchMutationType.COMPLETE -> Color(0xFF2E7D32) // green
    BatchMutationType.SKIP -> Color(0xFF9E9E9E) // grey
    BatchMutationType.PRIORITY_CHANGE -> Color(0xFF6A1B9A) // purple
    BatchMutationType.TAG_CHANGE -> Color(0xFF1565C0) // blue
    BatchMutationType.PROJECT_MOVE -> Color(0xFF00838F) // teal
    BatchMutationType.ARCHIVE -> Color(0xFF455A64) // blue grey
    BatchMutationType.STATE_CHANGE -> Color(0xFF6D4C41) // brown — medication tier override
    null -> Color.Gray
}
