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
fun AccessibilitySection(
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean,
    largeTouchTargetsEnabled: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onLargeTouchTargetsChange: (Boolean) -> Unit
) {
    SectionHeader("Accessibility")

    SettingsToggleRow(
        title = "Reduce Motion",
        subtitle = "Disable non-essential animations",
        checked = reduceMotionEnabled,
        onCheckedChange = onReduceMotionChange
    )
    SettingsToggleRow(
        title = "High Contrast Mode",
        subtitle = "Stronger borders and bolder priority colors",
        checked = highContrastEnabled,
        onCheckedChange = onHighContrastChange
    )
    SettingsToggleRow(
        title = "Large Touch Targets",
        subtitle = "Increase minimum interactive element size",
        checked = largeTouchTargetsEnabled,
        onCheckedChange = onLargeTouchTargetsChange
    )
    Text(
        text = "PrismTask supports TalkBack, Switch Access, and keyboard navigation.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    HorizontalDivider()
}
