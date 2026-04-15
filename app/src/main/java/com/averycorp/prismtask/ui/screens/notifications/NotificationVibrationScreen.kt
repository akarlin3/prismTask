package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.averycorp.prismtask.domain.model.notifications.VibrationIntensity
import com.averycorp.prismtask.domain.model.notifications.VibrationPreset
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun NotificationVibrationScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var preset by remember { mutableStateOf(profile.vibrationPreset) }
    var intensity by remember { mutableStateOf(profile.vibrationIntensity) }
    var repeatCount by remember { mutableStateOf(profile.vibrationRepeatCount.toFloat()) }
    var continuous by remember { mutableStateOf(profile.vibrationContinuous) }

    NotificationSubScreenScaffold("Vibration & Haptics", navController) {
        SubHeader("Pattern")
        VibrationPreset.values().forEach { p ->
            RadioRow(
                label = p.label,
                selected = preset == p,
                onSelect = { preset = p }
            )
        }
        if (preset == VibrationPreset.CUSTOM) {
            Text(
                "Custom patterns are recorded tap-by-tap in the pattern recorder. Pattern data is stored on the active profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionSpacer()
        SubHeader("Intensity")
        VibrationIntensity.values().forEach { i ->
            RadioRow(
                label = i.label,
                selected = intensity == i,
                onSelect = { intensity = i }
            )
        }

        SectionSpacer()
        SubHeader("Repetition")
        SettingsToggleRow(
            title = "Continuous until dismissed",
            subtitle = "Keep buzzing until the notification is tapped or swiped",
            checked = continuous,
            onCheckedChange = { continuous = it }
        )
        if (!continuous) {
            LabeledSlider(
                label = "Repeat count",
                value = repeatCount,
                valueRange = 1f..10f,
                steps = 8,
                format = { "${it.toInt()}x" },
                onChange = { repeatCount = it }
            )
        }

        SectionSpacer()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.previewProfile(
                        profile.copy(
                            vibrationPreset = preset,
                            vibrationIntensity = intensity,
                            vibrationRepeatCount = repeatCount.toInt(),
                            vibrationContinuous = continuous
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Preview") }
            Button(
                onClick = {
                    val entity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                    viewModel.commitProfileEdit(
                        entity.copy(
                            vibrationPresetKey = preset.key,
                            vibrationIntensityKey = intensity.key,
                            vibrationRepeatCount = repeatCount.toInt(),
                            vibrationContinuous = continuous
                        )
                    )
                    navController.popBackStack()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}
