package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.notifications.QuietHoursWindow
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import com.averycorp.prismtask.domain.usecase.NotificationProfileResolver
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationQuietHoursScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var enabled by remember { mutableStateOf(profile.quietHours.enabled) }
    var startHour by remember { mutableStateOf(profile.quietHours.start.hour.toFloat()) }
    var endHour by remember { mutableStateOf(profile.quietHours.end.hour.toFloat()) }
    var days by remember { mutableStateOf(profile.quietHours.days.toMutableSet()) }
    var breakThrough by remember { mutableStateOf(profile.quietHours.priorityOverrideTiers.toMutableSet()) }

    NotificationSubScreenScaffold("Quiet hours", navController) {
        SettingsToggleRow(
            title = "Enable quiet hours",
            subtitle = "Defer notifications to the end of the window",
            checked = enabled,
            onCheckedChange = { enabled = it }
        )

        if (enabled) {
            SubHeader("Window")
            LabeledSlider(
                label = "Starts at",
                value = startHour,
                valueRange = 0f..23f,
                steps = 22,
                format = { "${it.toInt()}:00" },
                onChange = { startHour = it }
            )
            LabeledSlider(
                label = "Ends at",
                value = endHour,
                valueRange = 0f..23f,
                steps = 22,
                format = { "${it.toInt()}:00" },
                onChange = { endHour = it }
            )
            Text(
                text = if (startHour > endHour) {
                    "Overnight window \u2014 starts at ${startHour.toInt()}:00 today and ends at ${endHour.toInt()}:00 tomorrow."
                } else {
                    "Same-day window."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SubHeader("Days")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.values().forEach { day ->
                    val checked = day in days
                    FilterChip(
                        selected = checked,
                        onClick = {
                            days = days.toMutableSet().also {
                                if (checked) it.remove(day) else it.add(day)
                            }
                        },
                        label = { Text(day.name.take(3)) }
                    )
                }
            }

            SubHeader("Break-through allowlist")
            Text(
                "Urgency tiers that can still fire during quiet hours. " +
                    "Allow High and Critical so medication doses and " +
                    "time-sensitive reminders aren't silenced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UrgencyTier.values().forEach { tier ->
                    val checked = tier in breakThrough
                    FilterChip(
                        selected = checked,
                        onClick = {
                            breakThrough = breakThrough.toMutableSet().also {
                                if (checked) it.remove(tier) else it.add(tier)
                            }
                        },
                        label = { Text(tier.label) }
                    )
                }
            }
        }

        Button(
            onClick = {
                val entity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                val window = QuietHoursWindow(
                    enabled = enabled,
                    start = LocalTime.of(startHour.toInt(), 0),
                    end = LocalTime.of(endHour.toInt(), 0),
                    days = days.toSet(),
                    priorityOverrideTiers = breakThrough.toSet()
                )
                val json = NotificationProfileResolver.DEFAULT.encodeQuietHours(window)
                viewModel.commitProfileEdit(entity.copy(quietHoursJson = json))
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Save") }
    }
}
