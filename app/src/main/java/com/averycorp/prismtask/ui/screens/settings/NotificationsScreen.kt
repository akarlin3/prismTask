package com.averycorp.prismtask.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            NotificationPermissionBanner()
            BatteryOptimizationBanner()

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
                onWeeklySummaryToggle = viewModel::setWeeklyHabitSummaryEnabled,
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

/**
 * Shown when the user has denied (or not yet granted) the
 * POST_NOTIFICATIONS runtime permission on API 33+. Reminders can still
 * be scheduled — the banner just warns that they won't fire and offers a
 * deep-link into the system app settings so the user can grant it.
 *
 * The permission state is re-checked on ON_RESUME so returning from
 * Settings with the permission granted hides the banner immediately.
 */
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Notifications Blocked",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PrismTask can't deliver reminders until you allow notifications. " +
                    "Your tasks and reminders are still saved — they just won't fire.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text("Allow")
                }
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("Open Settings")
                }
            }
        }
    }
}

/**
 * Shown when the app is NOT exempt from battery optimization. Samsung
 * (and other OEMs) aggressively delay or drop scheduled alarms for apps
 * under optimization, so this nudges the user toward the ignore list.
 *
 * The persistent surface complements the one-time onboarding dialog in
 * MainActivity — users who dismissed that prompt can grant the exemption
 * here any time.
 */
@Composable
private fun BatteryOptimizationBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var exempt by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (exempt) return

    val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Improve Reminder Reliability",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSamsung) {
                    "Samsung devices may delay notifications. Tap below to exempt " +
                        "PrismTask from battery optimization so reminders fire on time."
                } else {
                    "Some devices delay notifications to save battery. Tap below to " +
                        "exempt PrismTask from battery optimization so reminders fire " +
                        "on time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (isSamsung) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Samsung also keeps a \"Put Unused Apps to Sleep\" and " +
                        "\"Deep Sleeping Apps\" list under Settings \u2192 Battery. " +
                        "If reminders still miss, check PrismTask isn't on those " +
                        "lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isSamsung) {
                    TextButton(onClick = {
                        // Best-effort deep-link to Settings -> Battery. Exact path
                        // varies by OEM; fall back to generic Settings root.
                        val intent = Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }) {
                        Text("Battery Settings")
                    }
                }
                TextButton(onClick = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    ).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("Exempt PrismTask")
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
