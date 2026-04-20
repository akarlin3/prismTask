package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.preferences.CelebrationIntensity
import com.averycorp.prismtask.data.preferences.GoodEnoughEscalation
import com.averycorp.prismtask.data.preferences.NdPreferences

@Composable
fun FocusReleaseSubSettings(
    ndPrefs: NdPreferences,
    onGoodEnoughTimersChange: (Boolean) -> Unit,
    onDefaultGoodEnoughMinutesChange: (Int) -> Unit,
    onGoodEnoughEscalationChange: (GoodEnoughEscalation) -> Unit,
    onAntiReworkChange: (Boolean) -> Unit,
    onSoftWarningChange: (Boolean) -> Unit,
    onCoolingOffChange: (Boolean) -> Unit,
    onCoolingOffMinutesChange: (Int) -> Unit,
    onRevisionCounterChange: (Boolean) -> Unit,
    onMaxRevisionsChange: (Int) -> Unit,
    onShipItCelebrationsChange: (Boolean) -> Unit,
    onCelebrationIntensityChange: (CelebrationIntensity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp)
    ) {
        // --- Good Enough Timers ---
        SubSectionHeader("Good Enough Timers")

        SubToggleRow(
            title = "Enable Good Enough Timers",
            checked = ndPrefs.goodEnoughTimersEnabled,
            onCheckedChange = onGoodEnoughTimersChange,
            accessibilityLabel = "Toggle good enough timers"
        )

        AnimatedVisibility(visible = ndPrefs.goodEnoughTimersEnabled) {
            Column {
                LabeledSlider(
                    label = "Nudge me after",
                    value = ndPrefs.defaultGoodEnoughMinutes.toFloat(),
                    valueRange = 5f..120f,
                    steps = 22,
                    valueLabel = "${ndPrefs.defaultGoodEnoughMinutes} min",
                    onValueChange = { onDefaultGoodEnoughMinutesChange(it.toInt()) },
                    accessibilityLabel = "Default time cap: ${ndPrefs.defaultGoodEnoughMinutes} minutes"
                )

                SubSectionLabel("Escalation Level")
                EscalationRadioGroup(
                    selected = ndPrefs.goodEnoughEscalation,
                    onSelected = onGoodEnoughEscalationChange
                )

                SubInfoChip("You can set a custom timer on any individual task")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Anti-Rework Guards ---
        SubSectionHeader("Anti-Rework Guards")

        SubToggleRow(
            title = "Enable Anti-Rework Guards",
            checked = ndPrefs.antiReworkEnabled,
            onCheckedChange = onAntiReworkChange,
            accessibilityLabel = "Toggle anti-rework guards"
        )

        AnimatedVisibility(visible = ndPrefs.antiReworkEnabled) {
            Column {
                SubToggleRow(
                    title = "Soft Warning",
                    subtitle = "Show a reminder when I re-open a completed task",
                    checked = ndPrefs.softWarningEnabled,
                    onCheckedChange = onSoftWarningChange,
                    accessibilityLabel = "Toggle soft warning for completed tasks"
                )

                SubToggleRow(
                    title = "Cooling-Off Period",
                    subtitle = "Wait before allowing edits to completed tasks",
                    checked = ndPrefs.coolingOffEnabled,
                    onCheckedChange = onCoolingOffChange,
                    accessibilityLabel = "Toggle cooling-off period"
                )

                AnimatedVisibility(visible = ndPrefs.coolingOffEnabled) {
                    LabeledSlider(
                        label = "Cool off for",
                        value = ndPrefs.coolingOffMinutes.toFloat(),
                        valueRange = 15f..120f,
                        steps = 6,
                        valueLabel = "${ndPrefs.coolingOffMinutes} min",
                        onValueChange = { onCoolingOffMinutesChange(it.toInt()) },
                        accessibilityLabel = "Cooling-off period: ${ndPrefs.coolingOffMinutes} minutes"
                    )
                }

                SubToggleRow(
                    title = "Revision Counter",
                    subtitle = "Limit how many times I can re-edit a task",
                    checked = ndPrefs.revisionCounterEnabled,
                    onCheckedChange = onRevisionCounterChange,
                    accessibilityLabel = "Toggle revision counter"
                )

                AnimatedVisibility(visible = ndPrefs.revisionCounterEnabled) {
                    LabeledSlider(
                        label = "Max revisions",
                        value = ndPrefs.maxRevisions.toFloat(),
                        valueRange = 1f..10f,
                        steps = 8,
                        valueLabel = "${ndPrefs.maxRevisions}",
                        onValueChange = { onMaxRevisionsChange(it.toInt()) },
                        accessibilityLabel = "Maximum revisions: ${ndPrefs.maxRevisions}"
                    )
                }

                SubInfoChip("You can customize revision limits per task")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Ship-It Celebrations ---
        SubSectionHeader("Ship-It Celebrations")

        SubToggleRow(
            title = "Enable Ship-It Celebrations",
            checked = ndPrefs.shipItCelebrationsEnabled,
            onCheckedChange = onShipItCelebrationsChange,
            accessibilityLabel = "Toggle ship-it celebrations"
        )

        AnimatedVisibility(visible = ndPrefs.shipItCelebrationsEnabled) {
            Column {
                SubSectionLabel("Celebration Intensity")
                IntensityRadioGroup(
                    selected = ndPrefs.celebrationIntensity,
                    onSelected = onCelebrationIntensityChange,
                    calmModeActive = ndPrefs.calmModeEnabled
                )
            }
        }
    }
}

@Composable
private fun SubSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SubSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SubToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accessibilityLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { contentDescription = accessibilityLabel },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    accessibilityLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = accessibilityLabel }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EscalationRadioGroup(
    selected: GoodEnoughEscalation,
    onSelected: (GoodEnoughEscalation) -> Unit
) {
    Column {
        EscalationOption(
            label = "Gentle Nudge",
            description = "Snackbar, easy to dismiss",
            value = GoodEnoughEscalation.NUDGE,
            selected = selected,
            onSelected = onSelected
        )
        EscalationOption(
            label = "Ask Me Directly",
            description = "Dialog: 'Keep working' or 'Ship it'",
            value = GoodEnoughEscalation.DIALOG,
            selected = selected,
            onSelected = onSelected
        )
        EscalationOption(
            label = "Lock Editing",
            description = "Must tap 'Override' to continue",
            value = GoodEnoughEscalation.LOCK,
            selected = selected,
            onSelected = onSelected
        )
    }
}

@Composable
private fun EscalationOption(
    label: String,
    description: String,
    value: GoodEnoughEscalation,
    selected: GoodEnoughEscalation,
    onSelected: (GoodEnoughEscalation) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(value) }
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$label: $description" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected == value,
            onClick = { onSelected(value) }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntensityRadioGroup(
    selected: CelebrationIntensity,
    onSelected: (CelebrationIntensity) -> Unit,
    calmModeActive: Boolean
) {
    Column {
        if (calmModeActive) {
            SubInfoChip("Celebrations are subtle while Calm Mode is active.")
        }

        IntensityOption(
            label = "Subtle",
            description = "Gentle checkmark animation",
            value = CelebrationIntensity.LOW,
            selected = if (calmModeActive) CelebrationIntensity.LOW else selected,
            onSelected = onSelected,
            enabled = !calmModeActive
        )
        IntensityOption(
            label = "Medium",
            description = "Confetti burst",
            value = CelebrationIntensity.MEDIUM,
            selected = if (calmModeActive) CelebrationIntensity.LOW else selected,
            onSelected = onSelected,
            enabled = !calmModeActive
        )
        IntensityOption(
            label = "Full Send",
            description = "Full-screen celebration with message",
            value = CelebrationIntensity.HIGH,
            selected = if (calmModeActive) CelebrationIntensity.LOW else selected,
            onSelected = onSelected,
            enabled = !calmModeActive
        )
    }
}

@Composable
private fun IntensityOption(
    label: String,
    description: String,
    value: CelebrationIntensity,
    selected: CelebrationIntensity,
    onSelected: (CelebrationIntensity) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelected(value) }
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$label: $description" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected == value,
            onClick = { onSelected(value) },
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                    if (enabled) it else it.copy(alpha = 0.5f)
                }
            )
        }
    }
}

@Composable
private fun SubInfoChip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.shapes.medium
            ).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u2139\uFE0F", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
