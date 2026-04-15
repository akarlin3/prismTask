package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.averycorp.prismtask.domain.model.notifications.BuiltInSound
import com.averycorp.prismtask.domain.model.notifications.SoundCategory
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

/**
 * Sound picker + preview.
 *
 * The left rail (category filter chips) is compact on phones and stays
 * visible while scrolling the sound list. Tapping a sound previews it
 * immediately via [NotificationSettingsViewModel.previewProfile]. The
 * "Save" button writes the choice back to the active profile entity and
 * pops.
 */
@Composable
fun NotificationSoundScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val customSounds by viewModel.customSounds.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf<SoundCategory?>(null) }
    var pendingSoundId by remember { mutableStateOf(profile.soundId) }
    var pendingVolume by remember { mutableStateOf(profile.soundVolumePercent.toFloat()) }
    var pendingFadeIn by remember { mutableStateOf(profile.soundFadeInMs.toFloat()) }
    var pendingFadeOut by remember { mutableStateOf(profile.soundFadeOutMs.toFloat()) }
    var pendingSilent by remember { mutableStateOf(profile.silent) }

    val visibleSounds = remember(selectedCategory) {
        when (selectedCategory) {
            null -> BuiltInSound.ALL
            SoundCategory.CUSTOM -> emptyList() // custom list rendered separately
            else -> BuiltInSound.byCategory(selectedCategory!!)
        }
    }

    NotificationSubScreenScaffold("Sound", navController) {
        SubHeader("Global")
        SettingsToggleRow(
            title = "Silent for this profile",
            subtitle = "Play no sound at all, even when the device is unmuted",
            checked = pendingSilent,
            onCheckedChange = { pendingSilent = it }
        )

        SubHeader("Volume & Fades")
        LabeledSlider(
            label = "Volume",
            value = pendingVolume,
            valueRange = 0f..100f,
            format = { "${it.toInt()}%" },
            onChange = { pendingVolume = it }
        )
        LabeledSlider(
            label = "Fade in",
            value = pendingFadeIn,
            valueRange = 0f..5000f,
            format = { "${(it / 1000f).format1()}s" },
            onChange = { pendingFadeIn = it }
        )
        LabeledSlider(
            label = "Fade out",
            value = pendingFadeOut,
            valueRange = 0f..5000f,
            format = { "${(it / 1000f).format1()}s" },
            onChange = { pendingFadeOut = it }
        )

        SectionSpacer()
        SubHeader("Category")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All") }
            )
            SoundCategory.values().forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.label) }
                )
            }
        }

        if (selectedCategory == SoundCategory.CUSTOM) {
            if (customSounds.isEmpty()) {
                Text(
                    "No custom uploads yet. Tap \u201cUpload\u201d below to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                customSounds.forEach { s ->
                    RadioRow(
                        label = s.name,
                        secondary = "${s.format.uppercase()} \u2014 ${(s.durationMs / 1000f).format1()}s",
                        selected = pendingSoundId == s.soundId(),
                        onSelect = { pendingSoundId = s.soundId() }
                    )
                }
            }
        } else {
            visibleSounds.forEach { sound ->
                RadioRow(
                    label = sound.displayName,
                    secondary = sound.category.label,
                    selected = pendingSoundId == sound.id,
                    onSelect = { pendingSoundId = sound.id }
                )
            }
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
                            soundId = pendingSoundId,
                            silent = pendingSilent,
                            soundVolumePercent = pendingVolume.toInt(),
                            soundFadeInMs = pendingFadeIn.toInt(),
                            soundFadeOutMs = pendingFadeOut.toInt()
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Preview") }
            Button(
                onClick = {
                    val currentEntity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                    viewModel.commitProfileEdit(
                        currentEntity.copy(
                            soundId = pendingSoundId,
                            silent = pendingSilent,
                            soundVolumePercent = pendingVolume.toInt(),
                            soundFadeInMs = pendingFadeIn.toInt(),
                            soundFadeOutMs = pendingFadeOut.toInt()
                        )
                    )
                    navController.popBackStack()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }
}

private fun Float.format1(): String = "%.1f".format(this)
