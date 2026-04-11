package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun VoiceInputSection(
    voiceInputEnabled: Boolean,
    voiceFeedbackEnabled: Boolean,
    continuousModeEnabled: Boolean,
    onVoiceInputEnabledChange: (Boolean) -> Unit,
    onVoiceFeedbackEnabledChange: (Boolean) -> Unit,
    onContinuousModeEnabledChange: (Boolean) -> Unit
) {
    SectionHeader("Voice Input")

    SettingsToggleRow(
        title = "Enable Voice Input",
        subtitle = "Show the microphone button on the quick-add bar",
        checked = voiceInputEnabled,
        onCheckedChange = onVoiceInputEnabledChange
    )
    SettingsToggleRow(
        title = "Voice Feedback",
        subtitle = "Read voice command responses aloud",
        checked = voiceFeedbackEnabled,
        onCheckedChange = onVoiceFeedbackEnabledChange
    )
    SettingsToggleRow(
        title = "Continuous Mode",
        subtitle = "Long-press the mic for hands-free voice control",
        checked = continuousModeEnabled,
        onCheckedChange = onContinuousModeEnabledChange
    )

    HorizontalDivider()
}
