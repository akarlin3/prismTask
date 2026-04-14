package com.averycorp.prismtask.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ResetOptions(
    val tasksAndProjects: Boolean = false,
    val habitsAndHistory: Boolean = false,
    val tags: Boolean = false,
    val templates: Boolean = false,
    val calendarSyncData: Boolean = false,
    val preferencesAndSettings: Boolean = false,
    val restartOnboarding: Boolean = false
) {
    val anySelected: Boolean get() =
        tasksAndProjects ||
            habitsAndHistory ||
            tags ||
            templates ||
            calendarSyncData ||
            preferencesAndSettings ||
            restartOnboarding
}

private enum class ResetDialogStep { OPTIONS, CONFIRM }

/**
 * Two-step reset dialog:
 * 1. Options screen – user selects what to delete (nothing pre-selected).
 * 2. Confirmation screen – dynamic summary + must type "RESET" to proceed.
 *
 * @param isResetting Whether a reset operation is in progress (disables button, shows spinner).
 * @param onReset     Called with the final [ResetOptions] when the user confirms.
 * @param onDismiss   Called when the user cancels at any step.
 */
@Composable
fun ResetAppDataDialog(
    isResetting: Boolean,
    onReset: (ResetOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ResetDialogStep.OPTIONS) }
    var options by remember { mutableStateOf(ResetOptions()) }
    var confirmText by remember { mutableStateOf("") }

    when (step) {
        ResetDialogStep.OPTIONS -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Reset App Data") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Select what to reset. Nothing will be deleted until you confirm.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ResetOptionRow(
                            label = "Tasks, Subtasks & Projects",
                            checked = options.tasksAndProjects,
                            onCheckedChange = { options = options.copy(tasksAndProjects = it) }
                        )
                        ResetOptionRow(
                            label = "Habits & Habit History",
                            checked = options.habitsAndHistory,
                            onCheckedChange = { options = options.copy(habitsAndHistory = it) }
                        )
                        ResetOptionRow(
                            label = "Tags",
                            checked = options.tags,
                            onCheckedChange = { options = options.copy(tags = it) }
                        )
                        ResetOptionRow(
                            label = "Templates",
                            checked = options.templates,
                            onCheckedChange = { options = options.copy(templates = it) }
                        )
                        ResetOptionRow(
                            label = "Calendar Sync Data",
                            checked = options.calendarSyncData,
                            onCheckedChange = { options = options.copy(calendarSyncData = it) }
                        )
                        ResetOptionRow(
                            label = "Preferences & Settings",
                            checked = options.preferencesAndSettings,
                            onCheckedChange = { options = options.copy(preferencesAndSettings = it) }
                        )
                        ResetOptionRow(
                            label = "Restart Onboarding",
                            checked = options.restartOnboarding,
                            onCheckedChange = { options = options.copy(restartOnboarding = it) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmText = ""
                            step = ResetDialogStep.CONFIRM
                        },
                        enabled = options.anySelected
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            )
        }

        ResetDialogStep.CONFIRM -> {
            val bulletPoints = buildConfirmationBullets(options)
            val confirmEnabled = confirmText.trim() == "RESET" && !isResetting

            AlertDialog(
                onDismissRequest = { if (!isResetting) onDismiss() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Are You Sure?")
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "This will permanently delete:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        bulletPoints.forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "This action cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Type RESET to confirm:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            singleLine = true,
                            placeholder = { Text("RESET") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isResetting
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onReset(options) },
                        enabled = confirmEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (isResetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resetting...")
                        } else {
                            Text("Reset Selected Data")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { step = ResetDialogStep.OPTIONS },
                        enabled = !isResetting
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ResetOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun buildConfirmationBullets(options: ResetOptions): List<String> =
    buildList {
        if (options.tasksAndProjects) add("All tasks, subtasks & projects")
        if (options.habitsAndHistory) add("All habits & habit history")
        if (options.tags) add("All tags")
        if (options.templates) add("All templates")
        if (options.calendarSyncData) add("Calendar sync data")
        if (options.preferencesAndSettings) add("App preferences will be reset to defaults")
        if (options.restartOnboarding) add("Onboarding will restart on next launch")
    }
