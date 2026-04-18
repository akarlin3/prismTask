package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.dialogs.ResetAppDataDialog
import com.averycorp.prismtask.ui.components.dialogs.ResetOptions
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRow
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle
import com.averycorp.prismtask.ui.screens.settings.SettingsViewModel.DuplicateCleanupState

@Composable
fun DataSection(
    autoArchiveDays: Int,
    archivedCount: Int,
    isResetting: Boolean,
    duplicateCleanupState: DuplicateCleanupState,
    onAutoArchiveDaysChange: (Int) -> Unit,
    onResetAppData: (ResetOptions) -> Unit,
    onScanDuplicates: () -> Unit,
    onConfirmDeleteDuplicates: () -> Unit,
    onDismissDuplicateDialog: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToArchive: () -> Unit
) {
    var showAutoArchiveDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        ResetAppDataDialog(
            isResetting = isResetting,
            onReset = { options ->
                showResetDialog = false
                onResetAppData(options)
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showAutoArchiveDialog) {
        val options = listOf(3 to "3 days", 7 to "7 days", 14 to "14 days", 30 to "30 days", 0 to "Never")
        AlertDialog(
            onDismissRequest = { showAutoArchiveDialog = false },
            confirmButton = {
                TextButton(onClick = { showAutoArchiveDialog = false }) { Text("Close") }
            },
            title = { Text("Auto-Archive Completed Tasks") },
            text = {
                Column {
                    options.forEach { (days, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAutoArchiveDaysChange(days)
                                    showAutoArchiveDialog = false
                                }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = autoArchiveDays == days,
                                onClick = {
                                    onAutoArchiveDaysChange(days)
                                    showAutoArchiveDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        )
    }

    SectionHeader("Data")

    SettingsRow(title = "Manage Tags", onClick = onNavigateToTags)
    SettingsRow(title = "Manage Projects", onClick = onNavigateToProjects)
    SettingsRow(title = "Templates", onClick = onNavigateToTemplates)
    SettingsRowWithSubtitle(
        title = "Auto-archive",
        subtitle = if (autoArchiveDays == 0) "Never" else "After $autoArchiveDays days",
        onClick = { showAutoArchiveDialog = true }
    )
    SettingsRowWithSubtitle(
        title = "Archive",
        subtitle = "$archivedCount archived tasks",
        onClick = onNavigateToArchive
    )

    Spacer(modifier = Modifier.height(8.dp))

    val isCleanupBusy = duplicateCleanupState.isScanning || duplicateCleanupState.isDeleting

    OutlinedButton(
        onClick = onScanDuplicates,
        enabled = !isCleanupBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isCleanupBusy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (duplicateCleanupState.isDeleting) "Deleting..." else "Scanning...")
        } else {
            Text("Clean Up Duplicates")
        }
    }

    if (duplicateCleanupState.noDuplicatesFound) {
        AlertDialog(
            onDismissRequest = onDismissDuplicateDialog,
            confirmButton = {
                TextButton(onClick = onDismissDuplicateDialog) { Text("OK") }
            },
            title = { Text("No Duplicates Found") },
            text = {
                Text(
                    "No duplicate tasks, habits, or projects were detected. Duplicates " +
                        "are matched by the same title and due date (tasks), same name " +
                        "and frequency (habits), or same name (projects)."
                )
            }
        )
    }

    val preview = duplicateCleanupState.pendingPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { if (!duplicateCleanupState.isDeleting) onDismissDuplicateDialog() },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteDuplicates,
                    enabled = !duplicateCleanupState.isDeleting
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDuplicateDialog,
                    enabled = !duplicateCleanupState.isDeleting
                ) { Text("Cancel") }
            },
            title = { Text("Delete Duplicates?") },
            text = {
                Column {
                    val taskLine = when (preview.taskCount) {
                        0 -> null
                        1 -> "1 duplicate task"
                        else -> "${preview.taskCount} duplicate tasks"
                    }
                    val habitLine = when (preview.habitCount) {
                        0 -> null
                        1 -> "1 duplicate habit"
                        else -> "${preview.habitCount} duplicate habits"
                    }
                    val projectLine = when (preview.projectCount) {
                        0 -> null
                        1 -> "1 duplicate project"
                        else -> "${preview.projectCount} duplicate projects"
                    }
                    val parts = listOfNotNull(taskLine, habitLine, projectLine).joinToString(" and ")
                    Text("Found $parts.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The most complete copy in each group will be kept; the " +
                            "others will be deleted. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = { showResetDialog = true },
        enabled = !isResetting,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        if (isResetting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Resetting\u2026", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Reset App Data\u2026", color = MaterialTheme.colorScheme.error)
        }
    }

    HorizontalDivider()
}
