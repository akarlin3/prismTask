package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

/**
 * The user-facing notification controls. Three dimensions:
 *  - which notification types fire (toggles),
 *  - how intrusive every fired notification is (importance picker),
 *  - the default reminder lead time pre-filled into newly-created tasks.
 *
 * Pro/Premium-gated rows (briefing, evening summary, re-engagement) are
 * disabled below the upgrade tier so the user can see what's available
 * without the toggles silently no-op'ing.
 */
@Composable
fun NotificationSettingsSection(
    importance: String,
    defaultReminderOffset: Long,
    taskRemindersEnabled: Boolean,
    timerAlertsEnabled: Boolean,
    medicationRemindersEnabled: Boolean,
    habitNagSuppressionDays: Int = 7,
    dailyBriefingEnabled: Boolean,
    eveningSummaryEnabled: Boolean,
    weeklySummaryEnabled: Boolean,
    weeklyTaskSummaryEnabled: Boolean,
    overloadAlertsEnabled: Boolean,
    reengagementEnabled: Boolean,
    fullScreenNotificationsEnabled: Boolean,
    overrideVolumeEnabled: Boolean,
    repeatingVibrationEnabled: Boolean,
    userTier: UserTier,
    onImportanceChange: (String) -> Unit,
    onDefaultReminderOffsetChange: (Long) -> Unit,
    onTaskRemindersToggle: (Boolean) -> Unit,
    onTimerAlertsToggle: (Boolean) -> Unit,
    onMedicationRemindersToggle: (Boolean) -> Unit,
    onHabitNagSuppressionDaysChange: (Int) -> Unit = {},
    onDailyBriefingToggle: (Boolean) -> Unit,
    onEveningSummaryToggle: (Boolean) -> Unit,
    onWeeklySummaryToggle: (Boolean) -> Unit,
    onWeeklyTaskSummaryToggle: (Boolean) -> Unit,
    onOverloadAlertsToggle: (Boolean) -> Unit,
    onReengagementToggle: (Boolean) -> Unit,
    onFullScreenNotificationsToggle: (Boolean) -> Unit,
    onOverrideVolumeToggle: (Boolean) -> Unit,
    onRepeatingVibrationToggle: (Boolean) -> Unit,
    onOpenAdvanced: (() -> Unit)? = null
) {
    SectionHeader("Notifications")

    if (onOpenAdvanced != null) {
        SettingsRowWithSubtitle(
            title = "Customize Delivery",
            subtitle = "Profiles, sounds, vibration, quiet hours, escalation\u2026",
            onClick = onOpenAdvanced
        )
    }

    Text(
        text = "Importance",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            NotificationPreferences.IMPORTANCE_MINIMAL to "Minimal",
            NotificationPreferences.IMPORTANCE_STANDARD to "Standard",
            NotificationPreferences.IMPORTANCE_URGENT to "Urgent"
        ).forEach { (value, label) ->
            FilterChip(
                selected = importance == value,
                onClick = { onImportanceChange(value) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = when (importance) {
            NotificationPreferences.IMPORTANCE_MINIMAL ->
                "Minimal — silent, no heads-up popup. Quiet notifications you'll see in the shade."
            NotificationPreferences.IMPORTANCE_URGENT ->
                "Urgent — sound and a heads-up popup. Use sparingly so notifications keep their impact."
            else ->
                "Standard — sound, no heads-up popup. The system default."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    var showOffsetDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showOffsetDialog = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Default Reminder Lead Time",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatReminderOffset(defaultReminderOffset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showOffsetDialog) {
        ReminderOffsetDialog(
            current = defaultReminderOffset,
            onSelect = {
                onDefaultReminderOffsetChange(it)
                showOffsetDialog = false
            },
            onDismiss = { showOffsetDialog = false }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Notification Types",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )

    SettingsToggleRow(
        title = "Task Reminders",
        subtitle = "Reminders before tasks are due",
        checked = taskRemindersEnabled,
        onCheckedChange = onTaskRemindersToggle
    )

    SettingsToggleRow(
        title = "Timer Alerts",
        subtitle = "When a timer or focus session completes",
        checked = timerAlertsEnabled,
        onCheckedChange = onTimerAlertsToggle
    )

    SettingsToggleRow(
        title = "Medication Reminders",
        subtitle = "Medication and timed habit reminders",
        checked = medicationRemindersEnabled,
        onCheckedChange = onMedicationRemindersToggle
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Habit Reminders",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )

    SettingsToggleRow(
        title = "Delay If Scheduled",
        subtitle = if (habitNagSuppressionDays > 0) {
            "Suppress nag if booked within $habitNagSuppressionDays days"
        } else {
            "Disabled \u2014 nag notifications fire immediately"
        },
        checked = habitNagSuppressionDays > 0,
        onCheckedChange = { enabled ->
            onHabitNagSuppressionDaysChange(if (enabled) 7 else 0)
        }
    )

    if (habitNagSuppressionDays > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = "Window:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 8.dp)
            )
            Slider(
                value = habitNagSuppressionDays.toFloat(),
                onValueChange = { onHabitNagSuppressionDaysChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$habitNagSuppressionDays d",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val isPro = userTier == UserTier.PRO

    GatedToggleRow(
        title = "Daily Briefing",
        subtitle = "Morning briefing (Pro)",
        checked = dailyBriefingEnabled && isPro,
        enabled = isPro,
        onCheckedChange = onDailyBriefingToggle
    )

    GatedToggleRow(
        title = "Evening Summary",
        subtitle = "End-of-day summary (Pro)",
        checked = eveningSummaryEnabled && isPro,
        enabled = isPro,
        onCheckedChange = onEveningSummaryToggle
    )

    SettingsToggleRow(
        title = "Weekly Habit Summary",
        subtitle = "Weekly habit recap (Sunday 7 PM)",
        checked = weeklySummaryEnabled,
        onCheckedChange = onWeeklySummaryToggle
    )

    SettingsToggleRow(
        title = "Weekly Task Summary",
        subtitle = "Weekly task recap (Sunday 7:30 PM)",
        checked = weeklyTaskSummaryEnabled,
        onCheckedChange = onWeeklyTaskSummaryToggle
    )

    SettingsToggleRow(
        title = "Balance Alerts",
        subtitle = "Nudge when work-life balance is skewing",
        checked = overloadAlertsEnabled,
        onCheckedChange = onOverloadAlertsToggle
    )

    GatedToggleRow(
        title = "Re-engagement",
        subtitle = "Gentle nudge after inactivity (Pro)",
        checked = reengagementEnabled && isPro,
        enabled = isPro,
        onCheckedChange = onReengagementToggle
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Alert Style",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )

    SettingsToggleRow(
        title = "Full-Screen Notifications",
        subtitle = "Reminders take over the screen, even on the lock screen",
        checked = fullScreenNotificationsEnabled,
        onCheckedChange = onFullScreenNotificationsToggle
    )

    SettingsToggleRow(
        title = "Override Volume",
        subtitle = "Play at alarm volume so reminders are heard even on silent",
        checked = overrideVolumeEnabled,
        onCheckedChange = onOverrideVolumeToggle
    )

    SettingsToggleRow(
        title = "Buzz Repeatedly",
        subtitle = "Long, repeating vibration pattern on each reminder",
        checked = repeatingVibrationEnabled,
        onCheckedChange = onRepeatingVibrationToggle
    )

    HorizontalDivider()
}

@Composable
private fun GatedToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ReminderOffsetDialog(
    current: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0L to "At due time",
        300_000L to "5 minutes before",
        900_000L to "15 minutes before",
        1_800_000L to "30 minutes before",
        3_600_000L to "1 hour before",
        86_400_000L to "1 day before",
        NotificationPreferences.OFFSET_NONE to "No default"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Reminder Lead Time") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == current,
                                onClick = { onSelect(value) }
                            ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == current,
                            onClick = { onSelect(value) }
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

private fun formatReminderOffset(offset: Long): String = when (offset) {
    NotificationPreferences.OFFSET_NONE -> "No default"
    0L -> "At due time"
    300_000L -> "5 minutes before"
    900_000L -> "15 minutes before"
    1_800_000L -> "30 minutes before"
    3_600_000L -> "1 hour before"
    86_400_000L -> "1 day before"
    else -> {
        val minutes = offset / 60_000
        if (minutes < 60) "$minutes minutes before" else "${minutes / 60} hours before"
    }
}
