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
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRow
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle

@Composable
fun DataSection(
    autoArchiveDays: Int,
    archivedCount: Int,
    isResetting: Boolean,
    onAutoArchiveDaysChange: (Int) -> Unit,
    onResetApp: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToArchive: () -> Unit
) {
    var showAutoArchiveDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset App") },
            text = {
                Text("This will permanently delete all tasks, projects, tags, habits, and settings, and sign you out. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetApp()
                    }
                ) {
                    Text("Reset Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
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
                                }
                                .padding(vertical = 8.dp),
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

    OutlinedButton(
        onClick = { showResetConfirmDialog = true },
        enabled = !isResetting,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        if (isResetting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Resetting...", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Reset App", color = MaterialTheme.colorScheme.error)
        }
    }

    HorizontalDivider()
}
