package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.notifications.EscalationStepAction

/**
 * Preview & Test panel — mirrors the "Preview & Test" spec item.
 *
 * - Preview fires only the sound + vibration, with no tray notification.
 * - Test fires a real notification so users can audit the full heads-up
 *   / full-screen / lock-screen experience for the active profile.
 * - "Simulate escalation" walks the user through each step of the
 *   configured chain without having to wait for the alarm.
 */
@Composable
fun NotificationTesterScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val status by viewModel.testStatus.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Test", navController) {
        Text(
            "Active profile: ${profile.name}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Sound: ${if (profile.silent) "Silent" else profile.soundId} \u2014 Volume ${profile.soundVolumePercent}%",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Vibration: ${profile.vibrationPreset.label} (${profile.vibrationIntensity.label})",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Display: ${profile.displayMode.label} \u2022 Lock screen: ${profile.lockScreenVisibility.label}",
            style = MaterialTheme.typography.bodySmall
        )

        SectionSpacer()
        SubHeader("Actions")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.previewProfile(profile) },
                modifier = Modifier.weight(1f)
            ) { Text("Preview") }
            Button(
                onClick = { viewModel.testProfile(profile) },
                modifier = Modifier.weight(1f)
            ) { Text("Fire test") }
        }
        OutlinedButton(
            onClick = { viewModel.stopPreview() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Stop preview") }

        SectionSpacer()
        SubHeader("Escalation walkthrough")
        if (profile.escalation.enabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                profile.escalation.steps.forEachIndexed { index, step ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Step ${index + 1} \u2014 ${step.action.label}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Delay: ${step.delayMs / 60_000} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Targets: ${if (step.triggerTiers.isEmpty()) "All tiers" else step.triggerTiers.joinToString { it.label }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    viewModel.previewProfile(
                                        profile.copy(
                                            vibrationRepeatCount = when (step.action) {
                                                EscalationStepAction.GENTLE_PING -> 1
                                                EscalationStepAction.STANDARD_ALERT -> 2
                                                EscalationStepAction.LOUD_VIBRATE -> 4
                                                EscalationStepAction.FULL_SCREEN -> 6
                                            }
                                        )
                                    )
                                }
                            ) { Text("Preview this step") }
                        }
                    }
                }
            }
        } else {
            Text(
                "Escalation is disabled on this profile. Enable it from Notifications \u203a Escalation chain.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!status.isNullOrBlank()) {
            SectionSpacer()
            Text(status!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
