package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun AiNotificationsSection(
    eveningSummaryEnabled: Boolean,
    reengagementEnabled: Boolean,
    onEveningSummaryToggle: (Boolean) -> Unit,
    onReengagementToggle: (Boolean) -> Unit
) {
    SectionHeader("AI Notifications")

    Text(
        "Gentle, non-judgmental notifications powered by AI.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )

    SettingsToggleRow(
        title = "Evening Summary",
        subtitle = "A one-sentence summary of what you accomplished today (Pro)",
        checked = eveningSummaryEnabled,
        onCheckedChange = onEveningSummaryToggle
    )

    SettingsToggleRow(
        title = "Re-engagement Nudges",
        subtitle = "A gentle nudge if you haven't opened PrismTask in a while (Premium)",
        checked = reengagementEnabled,
        onCheckedChange = onReengagementToggle
    )

    HorizontalDivider()
}
