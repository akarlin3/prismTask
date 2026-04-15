package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun NotificationStreakScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val enabled by viewModel.streakAlertsEnabled.collectAsStateWithLifecycle()
    val leadHours by viewModel.streakAtRiskLeadHours.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Streak & Gamification", navController) {
        SettingsToggleRow(
            title = "Streak alerts",
            subtitle = "Celebrate milestones (3, 7, 14, 30, 100 days\u2026) and warn when streaks are at risk",
            checked = enabled,
            onCheckedChange = viewModel::setStreakAlertsEnabled
        )

        if (enabled) {
            SubHeader("At-risk warning lead time")
            LabeledSlider(
                label = "Warn me when my streak has this long left",
                value = leadHours.toFloat(),
                valueRange = 1f..24f,
                steps = 22,
                format = { "${it.toInt()}h" },
                onChange = { viewModel.setStreakAtRiskLeadHours(it.toInt()) }
            )
        }
    }
}
