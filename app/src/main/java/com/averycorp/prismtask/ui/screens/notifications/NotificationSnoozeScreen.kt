package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotificationSnoozeScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val snoozeOptions by viewModel.snoozeDurationsMinutes.collectAsStateWithLifecycle()

    var reAlertInterval by remember { mutableStateOf(profile.reAlertIntervalMinutes.toFloat()) }
    var reAlertMax by remember { mutableStateOf(profile.reAlertMaxAttempts.toFloat()) }

    NotificationSubScreenScaffold("Snooze & Re-Alerts", navController) {
        SubHeader("Available snooze durations")
        Text(
            "Tap to toggle any duration a user can pick from the snooze menu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val allCandidates = listOf(1, 5, 10, 15, 30, 45, 60, 90, 120, 180, 240)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allCandidates.forEach { minutes ->
                val checked = minutes in snoozeOptions
                FilterChip(
                    selected = checked,
                    onClick = {
                        val next = if (checked) snoozeOptions - minutes else snoozeOptions + minutes
                        viewModel.setSnoozeDurationsMinutes(next)
                    },
                    label = { Text(if (minutes < 60) "${minutes}m" else "${minutes / 60}h") }
                )
            }
        }

        SectionSpacer()
        SubHeader("Re-alert if not dismissed")
        LabeledSlider(
            label = "Re-alert every",
            value = reAlertInterval,
            valueRange = 1f..60f,
            steps = 58,
            format = { "${it.toInt()} min" },
            onChange = { reAlertInterval = it }
        )
        LabeledSlider(
            label = "Max attempts",
            value = reAlertMax,
            valueRange = 1f..10f,
            steps = 8,
            format = { "${it.toInt()}x" },
            onChange = { reAlertMax = it }
        )

        Button(
            onClick = {
                val entity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                viewModel.commitProfileEdit(
                    entity.copy(
                        reAlertIntervalMinutes = reAlertInterval.toInt(),
                        reAlertMaxAttempts = reAlertMax.toInt()
                    )
                )
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Save") }
    }
}
