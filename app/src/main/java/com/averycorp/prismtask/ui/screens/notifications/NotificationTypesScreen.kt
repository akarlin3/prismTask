package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

/**
 * Per-type enable/disable toggles. Mirrors the legacy
 * NotificationSettingsSection rows but lives under the new hub.
 */
@Composable
fun NotificationTypesScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val task by viewModel.taskRemindersEnabled.collectAsStateWithLifecycle()
    val timer by viewModel.timerAlertsEnabled.collectAsStateWithLifecycle()
    val med by viewModel.medicationRemindersEnabled.collectAsStateWithLifecycle()
    val briefing by viewModel.dailyBriefingEnabled.collectAsStateWithLifecycle()
    val evening by viewModel.eveningSummaryEnabled.collectAsStateWithLifecycle()
    val weekly by viewModel.weeklySummaryEnabled.collectAsStateWithLifecycle()
    val streak by viewModel.streakAlertsEnabled.collectAsStateWithLifecycle()
    val reengage by viewModel.reengagementEnabled.collectAsStateWithLifecycle()
    val overload by viewModel.overloadAlertsEnabled.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Notification Types", navController) {
        SubHeader("Tasks & Reminders")
        SettingsToggleRow(
            title = "Task Reminders",
            subtitle = "Fire before a task is due",
            checked = task,
            onCheckedChange = viewModel::setTaskRemindersEnabled
        )
        SettingsToggleRow(
            title = "Timer Alerts",
            subtitle = "When a timer or focus session completes",
            checked = timer,
            onCheckedChange = viewModel::setTimerAlertsEnabled
        )
        SettingsToggleRow(
            title = "Medication Reminders",
            subtitle = "Medication and timed habit reminders",
            checked = med,
            onCheckedChange = viewModel::setMedicationRemindersEnabled
        )

        SectionSpacer()
        SubHeader("AI & Summaries")
        SettingsToggleRow(
            title = "Daily Briefing",
            subtitle = "Morning AI briefing",
            checked = briefing,
            onCheckedChange = viewModel::setDailyBriefingEnabled
        )
        SettingsToggleRow(
            title = "Evening Summary",
            subtitle = "End-of-day recap",
            checked = evening,
            onCheckedChange = viewModel::setEveningSummaryEnabled
        )
        SettingsToggleRow(
            title = "Weekly Summary",
            subtitle = "Weekly habit recap (Sunday 7 PM)",
            checked = weekly,
            onCheckedChange = viewModel::setWeeklySummaryEnabled
        )

        SectionSpacer()
        SubHeader("Gamification & Life-Balance")
        SettingsToggleRow(
            title = "Streak & Milestones",
            subtitle = "Celebrate streak milestones and warn when streaks are at risk",
            checked = streak,
            onCheckedChange = viewModel::setStreakAlertsEnabled
        )
        SettingsToggleRow(
            title = "Balance Alerts",
            subtitle = "Nudge when work-life balance is skewing",
            checked = overload,
            onCheckedChange = viewModel::setOverloadAlertsEnabled
        )
        SettingsToggleRow(
            title = "Re-engagement",
            subtitle = "Gentle nudge after inactivity",
            checked = reengage,
            onCheckedChange = viewModel::setReengagementEnabled
        )
    }
}
