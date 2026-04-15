package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun NotificationBriefingScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val morningHour by viewModel.briefingMorningHour.collectAsStateWithLifecycle()
    val eveningHour by viewModel.briefingEveningHour.collectAsStateWithLifecycle()
    val middayEnabled by viewModel.briefingMiddayEnabled.collectAsStateWithLifecycle()
    val tone by viewModel.briefingTone.collectAsStateWithLifecycle()
    val sections by viewModel.briefingSections.collectAsStateWithLifecycle()
    val readAloud by viewModel.briefingReadAloudEnabled.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Daily Briefing", navController) {
        SubHeader("Delivery times")
        LabeledSlider(
            label = "Morning briefing",
            value = morningHour.toFloat(),
            valueRange = 0f..23f,
            steps = 22,
            format = { formatHourLabel(it.toInt()) },
            onChange = { viewModel.setBriefingMorningHour(it.toInt()) }
        )
        LabeledSlider(
            label = "Evening briefing",
            value = eveningHour.toFloat(),
            valueRange = 0f..23f,
            steps = 22,
            format = { formatHourLabel(it.toInt()) },
            onChange = { viewModel.setBriefingEveningHour(it.toInt()) }
        )
        SettingsToggleRow(
            title = "Add midday check-in",
            subtitle = "A short briefing at 12:30 PM",
            checked = middayEnabled,
            onCheckedChange = viewModel::setBriefingMiddayEnabled
        )

        SectionSpacer()
        SubHeader("Tone")
        listOf(
            NotificationPreferences.BRIEFING_TONE_CONCISE to "Concise \u2014 Just the facts",
            NotificationPreferences.BRIEFING_TONE_CONVERSATIONAL to "Conversational \u2014 Friendly rundown",
            NotificationPreferences.BRIEFING_TONE_MOTIVATIONAL to "Motivational \u2014 Pep-talk energy"
        ).forEach { (key, label) ->
            RadioRow(
                label = label,
                selected = tone == key,
                onSelect = { viewModel.setBriefingTone(key) }
            )
        }

        SectionSpacer()
        SubHeader("Sections")
        Text("Tap to mute sections you don't need.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        listOf(
            NotificationPreferences.BRIEFING_SECTION_TODAY_TASKS to "Today's tasks",
            NotificationPreferences.BRIEFING_SECTION_OVERDUE to "Overdue items",
            NotificationPreferences.BRIEFING_SECTION_UPCOMING to "Upcoming deadlines",
            NotificationPreferences.BRIEFING_SECTION_COLLAB to "Collaborator updates",
            NotificationPreferences.BRIEFING_SECTION_STREAK to "Streak status"
        ).forEach { (key, label) ->
            CheckableRow(
                label = label,
                checked = key in sections,
                onToggle = { viewModel.toggleBriefingSection(key, key !in sections) }
            )
        }

        SectionSpacer()
        SubHeader("Delivery")
        SettingsToggleRow(
            title = "Read aloud",
            subtitle = "Use TTS to read the briefing when tapped",
            checked = readAloud,
            onCheckedChange = viewModel::setBriefingReadAloud
        )
    }
}

private fun formatHourLabel(hour: Int): String {
    val h = hour.coerceIn(0, 23)
    val am = h < 12
    val display = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$display:00 ${if (am) "AM" else "PM"}"
}
