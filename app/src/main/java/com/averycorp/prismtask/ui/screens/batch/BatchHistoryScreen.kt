package com.averycorp.prismtask.ui.screens.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.averycorp.prismtask.data.local.entity.BatchUndoLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → "Batch command history" screen (A2 PR3).
 *
 * Lists batches from the last 24 hours, newest first. Tapping a batch
 * card opens a detail dialog with one row per affected entity.
 * The Undo button delegates to [BatchHistoryViewModel.undo]; the Snackbar
 * surfaces success/partial/failure feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchHistoryScreen(
    navController: NavHostController,
    viewModel: BatchHistoryViewModel = hiltViewModel()
) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedBatchId.collectAsStateWithLifecycle()
    val undoInProgress by viewModel.undoInProgressId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BatchHistoryViewModel.HistoryEvent.UndoFinished -> {
                    val msg = if (event.partial) {
                        "Restored ${event.restored} (some changes couldn't be reversed)"
                    } else {
                        "Restored ${event.restored} change${if (event.restored == 1) "" else "s"}"
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is BatchHistoryViewModel.HistoryEvent.UndoFailed ->
                    snackbarHostState.showSnackbar("Undo failed: ${event.reason}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch command history") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (batches.isEmpty()) {
            EmptyState(padding)
        } else {
            BatchList(
                padding = padding,
                batches = batches,
                undoInProgress = undoInProgress,
                onSelect = viewModel::selectBatch,
                onUndo = viewModel::undo
            )
        }

        val openId = selectedId
        if (openId != null) {
            val open = batches.firstOrNull { it.batchId == openId }
            if (open != null) {
                BatchDetailDialog(
                    summary = open,
                    onDismiss = { viewModel.selectBatch(null) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No recent batches", style = MaterialTheme.typography.titleMedium)
        Text(
            "Batch commands you run from QuickAdd appear here for 24 hours so you can undo them.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun BatchList(
    padding: PaddingValues,
    batches: List<BatchHistoryViewModel.BatchSummary>,
    undoInProgress: String?,
    onSelect: (String) -> Unit,
    onUndo: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(batches, key = { it.batchId }) { summary ->
            BatchCard(
                summary = summary,
                undoing = undoInProgress == summary.batchId,
                onTap = { onSelect(summary.batchId) },
                onUndo = { onUndo(summary.batchId) }
            )
        }
    }
}

@Composable
private fun BatchCard(
    summary: BatchHistoryViewModel.BatchSummary,
    undoing: Boolean,
    onTap: () -> Unit,
    onUndo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                summary.commandText.ifBlank { "(no command text recorded)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${summary.appliedCount} change${if (summary.appliedCount == 1) "" else "s"} • " +
                    formatRelative(summary.createdAt),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTap) { Text("Details") }
                if (summary.isUndone) {
                    Text(
                        "Undone " + formatRelative(summary.undoneAt ?: summary.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedButton(
                        enabled = !undoing,
                        onClick = onUndo
                    ) {
                        if (undoing) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        }
                        Text("Undo")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchDetailDialog(
    summary: BatchHistoryViewModel.BatchSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.commandText.ifBlank { "Batch detail" }) },
        text = {
            Column {
                Text(
                    "${summary.appliedCount} change${if (summary.appliedCount == 1) "" else "s"} • " +
                        formatAbsolute(summary.createdAt),
                    style = MaterialTheme.typography.bodySmall
                )
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    Column {
                        for (entry in summary.entries) {
                            EntryRow(entry)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun EntryRow(entry: BatchUndoLogEntry) {
    Text(
        "${entry.entityType} • ${entry.mutationType} • id=${entry.entityId ?: "?"}",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

private fun formatRelative(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    if (diff < 0) return formatAbsolute(millis)
    val mins = diff / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        else -> formatAbsolute(millis)
    }
}

private fun formatAbsolute(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
