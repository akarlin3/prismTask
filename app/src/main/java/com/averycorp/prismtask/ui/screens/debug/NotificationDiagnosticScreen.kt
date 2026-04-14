package com.averycorp.prismtask.ui.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Temporary diagnostic screen for troubleshooting notification delivery
 * (primarily Samsung S25 Ultra issues). Remove before release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDiagnosticScreen(
    navController: NavController,
    viewModel: NotificationDiagnosticViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownSeconds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Device: ${state.manufacturer} ${state.model} (API ${state.sdkInt})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Temporary screen for diagnosing why notifications don't appear. Remove before release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Permission Checks")

            StatusRow(
                label = "POST_NOTIFICATIONS",
                ok = !state.postNotificationsApplicable || state.postNotificationsGranted,
                detail = if (!state.postNotificationsApplicable)
                    "Not required below API 33"
                else if (state.postNotificationsGranted) "Granted" else "Denied"
            )
            StatusRow(
                label = "Exact Alarms",
                ok = state.exactAlarmsAllowed,
                detail = if (state.exactAlarmsAllowed)
                    "Can schedule exact alarms"
                else "Blocked — reminders will be inexact"
            )
            StatusRow(
                label = "Battery Optimization",
                ok = state.batteryOptimizationIgnored,
                detail = if (state.batteryOptimizationIgnored)
                    "App is excluded (unrestricted)"
                else "App may be throttled in Doze"
            )
            StatusRow(
                label = "App Notifications",
                ok = state.notificationsEnabled,
                detail = if (state.notificationsEnabled)
                    "Enabled at app level"
                else "BLOCKED at app level"
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = viewModel::runChecks,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Re-run Checks") }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Notification Channels (${state.channels.size})")

            if (state.channels.isEmpty()) {
                Text(
                    text = "No channels registered yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.channels.forEach { channel ->
                    ChannelCard(channel)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = viewModel::openAppNotificationSettings,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open App Notification Settings") }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Test Actions")

            Button(
                onClick = viewModel::fireTestNotification,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Fire Test Notification Now") }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::scheduleAlarmIn30Seconds,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Schedule Alarm in 30 Seconds") }

            countdown?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Alarm fires in ${it}s…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::startForegroundServiceTest,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start Foreground Service Test (60s)") }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Results Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = viewModel::clearLog) { Text("Clear") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (logEntries.isEmpty()) {
                        Text(
                            text = "No log entries yet. Run a test action above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logEntries.reversed().forEach { entry ->
                            Text(
                                text = "${formatTimestamp(entry.timestampMillis)}  ${entry.message}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (ok) Color(0xFF2E7D32) else Color(0xFFC62828))
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: ChannelInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (channel.blocked) Color(0xFFC62828) else Color(0xFF2E7D32))
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = channel.name.ifEmpty { "(unnamed)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "id: ${channel.id}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Importance: ${channel.importanceLabel} (${channel.importance})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (channel.blocked) {
                Text(
                    text = "Blocked — check system settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(millis))
