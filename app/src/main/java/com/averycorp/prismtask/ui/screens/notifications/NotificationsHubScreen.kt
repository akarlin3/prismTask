package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle

/**
 * Top-level Notifications hub. Links to each sub-screen of the
 * customizable notification system and shows a one-line summary of the
 * most salient state for each (active profile, quiet-hours status,
 * streak toggle, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsHubScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val enabledCount by viewModel.enabledTypeCount.collectAsStateWithLifecycle()
    val briefingHour by viewModel.briefingMorningHour.collectAsStateWithLifecycle()
    val streakEnabled by viewModel.streakAlertsEnabled.collectAsStateWithLifecycle()
    val watchMode by viewModel.watchSyncMode.collectAsStateWithLifecycle()
    val collabDigest by viewModel.collabDigestMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("Active Profile")
            SettingsRowWithSubtitle(
                title = activeProfile.name,
                subtitle = "Tap to manage profiles and switching rules"
            ) { navController.navigate(NotificationRoutes.PROFILES) }

            HorizontalDivider()
            SectionHeader("What You're Alerted About")
            SettingsRowWithSubtitle(
                title = "Notification Types",
                subtitle = "$enabledCount enabled \u2014 tasks, timers, briefings, more"
            ) { navController.navigate(NotificationRoutes.TYPES) }

            SettingsRowWithSubtitle(
                title = "Daily Briefing",
                subtitle = "Morning at ${formatHour(briefingHour)}"
            ) { navController.navigate(NotificationRoutes.BRIEFING) }

            SettingsRowWithSubtitle(
                title = "Streak & Gamification",
                subtitle = if (streakEnabled) "Milestones & risk alerts on" else "Opted out"
            ) { navController.navigate(NotificationRoutes.STREAK) }

            SettingsRowWithSubtitle(
                title = "Collaborator Updates",
                subtitle = "Digest: ${digestLabel(collabDigest)}"
            ) { navController.navigate(NotificationRoutes.COLLABORATOR) }

            HorizontalDivider()
            SectionHeader("How You're Alerted")
            SettingsRowWithSubtitle(
                title = "Sound",
                subtitle = "${if (activeProfile.silent) "Silent" else "Playing"} \u2014 Volume ${activeProfile.soundVolumePercent}%"
            ) { navController.navigate(NotificationRoutes.SOUND) }

            SettingsRowWithSubtitle(
                title = "Vibration & Haptics",
                subtitle = "${activeProfile.vibrationPreset.label} \u2014 ${activeProfile.vibrationIntensity.label}"
            ) { navController.navigate(NotificationRoutes.VIBRATION) }

            SettingsRowWithSubtitle(
                title = "Visual Display",
                subtitle = activeProfile.displayMode.label
            ) { navController.navigate(NotificationRoutes.VISUAL) }

            SettingsRowWithSubtitle(
                title = "Lock Screen & Badges",
                subtitle = activeProfile.lockScreenVisibility.label
            ) { navController.navigate(NotificationRoutes.LOCKSCREEN) }

            HorizontalDivider()
            SectionHeader("Timing")
            SettingsRowWithSubtitle(
                title = "Quiet Hours",
                subtitle = if (activeProfile.quietHours.enabled) {
                    "${activeProfile.quietHours.start} \u2192 ${activeProfile.quietHours.end}"
                } else "Off"
            ) { navController.navigate(NotificationRoutes.QUIET_HOURS) }

            SettingsRowWithSubtitle(
                title = "Snooze & Re-Alerts",
                subtitle = "Snooze: ${activeProfile.snoozeDurationsMinutes.joinToString(", ")}m \u2014 Re-alert every ${activeProfile.reAlertIntervalMinutes}m"
            ) { navController.navigate(NotificationRoutes.SNOOZE) }

            SettingsRowWithSubtitle(
                title = "Escalation Chain",
                subtitle = if (activeProfile.escalation.enabled) {
                    "${activeProfile.escalation.steps.size} steps"
                } else "Off"
            ) { navController.navigate(NotificationRoutes.ESCALATION) }

            HorizontalDivider()
            SectionHeader("Devices")
            SettingsRowWithSubtitle(
                title = "Smartwatch",
                subtitle = watchModeLabel(watchMode)
            ) { navController.navigate(NotificationRoutes.WATCH) }

            HorizontalDivider()
            SectionHeader("Preview & Test")
            SettingsRowWithSubtitle(
                title = "Test Active Profile",
                subtitle = "Live preview + fire a real notification"
            ) { navController.navigate(NotificationRoutes.TESTER) }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "All changes apply on the next scheduled reminder. Active profile and per-category overrides sync across devices once you're signed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatHour(hour: Int): String {
    val h = hour.coerceIn(0, 23)
    val am = h < 12
    val display = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$display:00 ${if (am) "AM" else "PM"}"
}

private fun digestLabel(mode: String): String = when (mode) {
    "hourly" -> "Hourly digest"
    "daily" -> "Daily digest"
    "muted" -> "Muted"
    else -> "Immediately"
}

private fun watchModeLabel(mode: String): String = when (mode) {
    "watch_only" -> "Watch only"
    "differentiated" -> "Separate watch settings"
    "disabled" -> "Watch alerts off"
    else -> "Mirroring phone"
}

/** Route constants for the notification sub-screens. */
object NotificationRoutes {
    const val HUB = "notifications_hub"
    const val PROFILES = "notifications_profiles"
    const val TYPES = "notifications_types"
    const val BRIEFING = "notifications_briefing"
    const val STREAK = "notifications_streak"
    const val COLLABORATOR = "notifications_collab"
    const val SOUND = "notifications_sound"
    const val VIBRATION = "notifications_vibration"
    const val VISUAL = "notifications_visual"
    const val LOCKSCREEN = "notifications_lockscreen"
    const val QUIET_HOURS = "notifications_quiet_hours"
    const val SNOOZE = "notifications_snooze"
    const val ESCALATION = "notifications_escalation"
    const val WATCH = "notifications_watch"
    const val TESTER = "notifications_tester"
}
