package com.averycorp.prismtask.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.settings.sections.NotificationSettingsSection
import com.averycorp.prismtask.ui.theme.ThemedSubScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userTier by viewModel.userTier.collectAsStateWithLifecycle()
    val notificationImportance by viewModel.notificationImportance.collectAsStateWithLifecycle()
    val defaultReminderOffset by viewModel.defaultReminderOffset.collectAsStateWithLifecycle()
    val taskRemindersEnabled by viewModel.taskRemindersEnabled.collectAsStateWithLifecycle()
    val timerAlertsEnabled by viewModel.timerAlertsEnabled.collectAsStateWithLifecycle()
    val medicationRemindersEnabled by viewModel.medicationRemindersEnabled.collectAsStateWithLifecycle()
    val habitNagSuppressionDays by viewModel.habitNagSuppressionDays.collectAsStateWithLifecycle()
    val dailyBriefingEnabled by viewModel.dailyBriefingEnabled.collectAsStateWithLifecycle()
    val eveningSummaryEnabled by viewModel.eveningSummaryEnabled.collectAsStateWithLifecycle()
    val weeklySummaryEnabled by viewModel.weeklySummaryEnabled.collectAsStateWithLifecycle()
    val overloadAlertsEnabled by viewModel.overloadAlertsEnabled.collectAsStateWithLifecycle()
    val reengagementEnabled by viewModel.reengagementEnabled.collectAsStateWithLifecycle()
    val fullScreenNotificationsEnabled by viewModel.fullScreenNotificationsEnabled.collectAsStateWithLifecycle()
    val overrideVolumeEnabled by viewModel.overrideVolumeEnabled.collectAsStateWithLifecycle()
    val repeatingVibrationEnabled by viewModel.repeatingVibrationEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ThemedSubScreenTitle("Notifications") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            NotificationSettingsSection(
                importance = notificationImportance,
                defaultReminderOffset = defaultReminderOffset,
                taskRemindersEnabled = taskRemindersEnabled,
                timerAlertsEnabled = timerAlertsEnabled,
                medicationRemindersEnabled = medicationRemindersEnabled,
                habitNagSuppressionDays = habitNagSuppressionDays,
                dailyBriefingEnabled = dailyBriefingEnabled,
                eveningSummaryEnabled = eveningSummaryEnabled,
                weeklySummaryEnabled = weeklySummaryEnabled,
                overloadAlertsEnabled = overloadAlertsEnabled,
                reengagementEnabled = reengagementEnabled,
                fullScreenNotificationsEnabled = fullScreenNotificationsEnabled,
                overrideVolumeEnabled = overrideVolumeEnabled,
                repeatingVibrationEnabled = repeatingVibrationEnabled,
                userTier = userTier,
                onImportanceChange = viewModel::setNotificationImportance,
                onDefaultReminderOffsetChange = viewModel::setDefaultReminderOffset,
                onTaskRemindersToggle = viewModel::setTaskRemindersEnabled,
                onTimerAlertsToggle = viewModel::setTimerAlertsEnabled,
                onMedicationRemindersToggle = viewModel::setMedicationRemindersEnabled,
                onHabitNagSuppressionDaysChange = viewModel::setHabitNagSuppressionDays,
                onDailyBriefingToggle = viewModel::setDailyBriefingEnabled,
                onEveningSummaryToggle = viewModel::setEveningSummaryEnabled,
                onWeeklySummaryToggle = viewModel::setWeeklySummaryEnabled,
                onOverloadAlertsToggle = viewModel::setOverloadAlertsEnabled,
                onReengagementToggle = viewModel::setReengagementEnabled,
                onFullScreenNotificationsToggle = viewModel::setFullScreenNotificationsEnabled,
                onOverrideVolumeToggle = viewModel::setOverrideVolumeEnabled,
                onRepeatingVibrationToggle = viewModel::setRepeatingVibrationEnabled,
                onOpenAdvanced = {
                    navController.navigate(PrismTaskRoute.NotificationsHub.route)
                }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
