package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle

@Composable
fun AiSection(
    claudeApiKey: String,
    onSetClaudeApiKey: (String) -> Unit,
    onClearClaudeApiKey: () -> Unit,
    onNavigateToEisenhower: () -> Unit,
    onNavigateToSmartPomodoro: () -> Unit,
    onNavigateToDailyBriefing: () -> Unit,
    onNavigateToWeeklyPlanner: () -> Unit,
    onNavigateToTimeline: () -> Unit
) {
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }

    SectionHeader("AI")

    val apiKeySubtitle = if (claudeApiKey.isNotBlank()) {
        "Configured (\u2022\u2022\u2022\u2022${claudeApiKey.takeLast(4)})"
    } else {
        "Not configured"
    }

    SettingsRowWithSubtitle(
        title = "Claude API Key",
        subtitle = apiKeySubtitle,
        onClick = {
            apiKeyInput = ""
            showApiKeyDialog = true
        }
    )

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Claude API Key") },
            text = {
                Column {
                    Text(
                        "Used for AI-powered import parsing. Get a key from console.anthropic.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("sk-ant-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) onSetClaudeApiKey(apiKeyInput.trim())
                        showApiKeyDialog = false
                    },
                    enabled = apiKeyInput.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (claudeApiKey.isNotBlank()) {
                        TextButton(onClick = {
                            onClearClaudeApiKey()
                            showApiKeyDialog = false
                        }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    SectionHeader("AI Features")

    Text(
        "AI features use Claude to analyze your tasks. Requires internet connection.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )

    SettingsRowWithSubtitle(
        title = "Eisenhower Matrix",
        subtitle = "AI-powered task categorization into urgency/importance quadrants",
        onClick = onNavigateToEisenhower
    )

    SettingsRowWithSubtitle(
        title = "Smart Focus Sessions",
        subtitle = "AI-planned Pomodoro sessions based on your tasks",
        onClick = onNavigateToSmartPomodoro
    )

    SettingsRowWithSubtitle(
        title = "Daily Briefing",
        subtitle = "Morning summary with top priorities and suggested task order",
        onClick = onNavigateToDailyBriefing
    )

    SettingsRowWithSubtitle(
        title = "Weekly Planner",
        subtitle = "AI-generated week plan distributing tasks across days",
        onClick = onNavigateToWeeklyPlanner
    )

    SettingsRowWithSubtitle(
        title = "Time Blocking",
        subtitle = "Auto-schedule your day with AI-optimized time blocks",
        onClick = onNavigateToTimeline
    )

    HorizontalDivider()
}
