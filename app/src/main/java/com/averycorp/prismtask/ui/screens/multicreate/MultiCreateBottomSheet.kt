package com.averycorp.prismtask.ui.screens.multicreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

/**
 * NavGraph-routed bottom sheet for the multi-task QuickAddBar flow
 * (Phase B / PR-C of the multi-task creation audit).
 *
 * Hosted as a regular composable destination — when the user dismisses
 * the sheet (drag-down, back, Cancel) the route is popped. On Approve
 * the VM creates the selected tasks and emits a non-null
 * `createdCount`, which we observe to trigger the same pop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiCreateBottomSheet(
    navController: NavController,
    initialText: String,
    viewModel: MultiCreateViewModel = hiltViewModel()
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val createdCount by viewModel.createdCount.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            viewModel.setInput(initialText)
            viewModel.extract()
        }
    }

    LaunchedEffect(createdCount) {
        // Empty selection → 0 → no-op (don't pop, keep sheet open so
        // the user can change their mind). Non-zero → tasks created,
        // pop back so the host screen sees the snackbar / refresh.
        if (createdCount != null && createdCount!! > 0) {
            navController.popBackStack()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { navController.popBackStack() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Multiple Tasks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Review the tasks pulled from your input. Toggle to skip, " +
                    "edit titles inline, then approve to create them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Extracting tasks…")
                    }
                }

                candidates.isEmpty() -> {
                    Text(
                        text = "No tasks recognized in the input.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Text(
                        text = "${candidates.size} task" +
                            "${if (candidates.size == 1) "" else "s"} found",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            count = candidates.size,
                            key = { idx -> idx }
                        ) { index ->
                            CandidateCard(
                                candidate = candidates[index],
                                onToggle = { viewModel.toggle(index) },
                                onTitleChange = { viewModel.editTitle(index, it) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { navController.popBackStack() }
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                val selectedCount = candidates.count { it.selected }
                Button(
                    onClick = { viewModel.createSelected() },
                    enabled = !isLoading && selectedCount > 0
                ) {
                    Text(
                        if (selectedCount == 0) "Approve"
                        else "Create $selectedCount Task${if (selectedCount == 1) "" else "s"}"
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: EditableMultiCreateCandidate,
    onToggle: () -> Unit,
    onTitleChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = candidate.selected,
                onCheckedChange = { onToggle() }
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = candidate.title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MetadataRow(candidate)
            }
        }
    }
}

@Composable
private fun MetadataRow(candidate: EditableMultiCreateCandidate) {
    val parts = buildList {
        if (candidate.suggestedDueDate != null) {
            add(
                java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                    .format(java.util.Date(candidate.suggestedDueDate))
            )
        }
        if (candidate.suggestedPriority > 0) {
            add("P${candidate.suggestedPriority}")
        }
        if (!candidate.suggestedProject.isNullOrBlank()) {
            add("@${candidate.suggestedProject}")
        }
        add("${(candidate.confidence * 100).toInt()}%")
    }
    Text(
        text = parts.joinToString(" • "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
