package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.preferences.ShakePreferences
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

/**
 * Settings UI for the shake-to-screenshot / report-a-bug gesture.
 *
 * Lets the user disable the gesture entirely and pick how sensitive the
 * shake detection should be.
 */
@Composable
fun ShakeSection(
    shakeEnabled: Boolean,
    shakeSensitivity: String,
    onShakeEnabledChange: (Boolean) -> Unit,
    onShakeSensitivityChange: (String) -> Unit
) {
    SectionHeader("Shake To Report")

    SettingsToggleRow(
        title = "Shake To Report A Bug",
        subtitle = "Shake the device to capture a screenshot and open the bug report form",
        checked = shakeEnabled,
        onCheckedChange = onShakeEnabledChange
    )

    AnimatedVisibility(visible = shakeEnabled) {
        Column {
            Text(
                text = "Sensitivity",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ShakePreferences.SENSITIVITY_LOW to "Low",
                    ShakePreferences.SENSITIVITY_MEDIUM to "Medium",
                    ShakePreferences.SENSITIVITY_HIGH to "High"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = shakeSensitivity == value,
                        onClick = { onShakeSensitivityChange(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (shakeSensitivity) {
                    ShakePreferences.SENSITIVITY_LOW ->
                        "Low — a firm, deliberate shake is required. Fewer accidental triggers."
                    ShakePreferences.SENSITIVITY_HIGH ->
                        "High — even a light shake will trigger a bug report."
                    else ->
                        "Medium — balanced detection that suits most users."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    HorizontalDivider()
}
