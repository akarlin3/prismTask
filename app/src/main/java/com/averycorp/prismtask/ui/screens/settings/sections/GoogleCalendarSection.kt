package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.calendar.CalendarInfo
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.screens.settings.formatLastSync
import com.averycorp.prismtask.ui.screens.settings.parseColorSafe

@Composable
fun GoogleCalendarSection(
    isGCalConnected: Boolean,
    gCalAccountEmail: String?,
    gCalSyncEnabled: Boolean,
    gCalSyncCalendarId: String,
    gCalAvailableCalendars: List<CalendarInfo>,
    gCalSyncDirection: String,
    gCalShowEvents: Boolean,
    gCalSyncCompletedTasks: Boolean,
    gCalSyncFrequency: String,
    gCalLastSyncTimestamp: Long,
    isGCalSyncing: Boolean,
    onConnectGoogleCalendar: () -> Unit,
    onDisconnectGoogleCalendar: () -> Unit,
    onSetGCalSyncEnabled: (Boolean) -> Unit,
    onLoadGCalCalendars: () -> Unit,
    onSetGCalSyncCalendarId: (String) -> Unit,
    onSetGCalSyncDirection: (String) -> Unit,
    onSetGCalShowEvents: (Boolean) -> Unit,
    onSetGCalSyncCompletedTasks: (Boolean) -> Unit,
    onSetGCalSyncFrequency: (String) -> Unit,
    onSyncGCalNow: () -> Unit
) {
    var showGCalCalendarPicker by remember { mutableStateOf(false) }
    var showGCalFrequencyPicker by remember { mutableStateOf(false) }

    SectionHeader("Google Calendar")

    if (!isGCalConnected) {
        Text(
            text = "Sync tasks with your Google Calendar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Button(
            onClick = onConnectGoogleCalendar,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Connect Google Calendar")
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gCalAccountEmail ?: "Connected",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onDisconnectGoogleCalendar) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sync Tasks to Calendar", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = gCalSyncEnabled,
                onCheckedChange = onSetGCalSyncEnabled
            )
        }

        AnimatedVisibility(visible = gCalSyncEnabled) {
            Column {
                val selectedCalendar = gCalAvailableCalendars.find { it.id == gCalSyncCalendarId }
                OutlinedButton(
                    onClick = {
                        onLoadGCalCalendars()
                        showGCalCalendarPicker = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    if (selectedCalendar != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(parseColorSafe(selectedCalendar.color))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calendar: ${selectedCalendar.name}")
                    } else {
                        Text("Calendar: ${gCalSyncCalendarId.ifEmpty { "Primary" }}")
                    }
                }

                Text(
                    "Sync Direction",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("push" to "Push Only", "pull" to "Pull Only", "both" to "Both").forEach { (value, label) ->
                        FilterChip(
                            selected = gCalSyncDirection == value,
                            onClick = { onSetGCalSyncDirection(value) },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Calendar Events in App", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = gCalShowEvents,
                        onCheckedChange = onSetGCalShowEvents
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sync Completed Tasks", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = gCalSyncCompletedTasks,
                        onCheckedChange = onSetGCalSyncCompletedTasks
                    )
                }

                val frequencyLabels = mapOf(
                    "realtime" to "Real-Time",
                    "15min" to "Every 15 Min",
                    "hourly" to "Hourly",
                    "manual" to "Manual Only"
                )
                OutlinedButton(
                    onClick = { showGCalFrequencyPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Sync Frequency: ${frequencyLabels[gCalSyncFrequency] ?: gCalSyncFrequency}")
                }

                if (gCalLastSyncTimestamp > 0) {
                    val lastSyncText = formatLastSync(gCalLastSyncTimestamp)
                    Text(
                        text = "Last Synced: $lastSyncText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSyncGCalNow,
                    enabled = !isGCalSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    if (isGCalSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Now")
                    }
                }
            }
        }
    }

    if (showGCalCalendarPicker && gCalAvailableCalendars.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showGCalCalendarPicker = false },
            title = { Text("Select Calendar") },
            text = {
                Column {
                    gCalAvailableCalendars.forEach { cal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetGCalSyncCalendarId(cal.id)
                                    showGCalCalendarPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseColorSafe(cal.color))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                                if (cal.isPrimary) {
                                    Text(
                                        "Primary",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (cal.id == gCalSyncCalendarId) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGCalCalendarPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showGCalFrequencyPicker) {
        AlertDialog(
            onDismissRequest = { showGCalFrequencyPicker = false },
            title = { Text("Sync Frequency") },
            text = {
                Column {
                    listOf(
                        "realtime" to "Real-Time",
                        "15min" to "Every 15 Minutes",
                        "hourly" to "Hourly",
                        "manual" to "Manual Only"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetGCalSyncFrequency(value)
                                    showGCalFrequencyPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = gCalSyncFrequency == value,
                                onClick = {
                                    onSetGCalSyncFrequency(value)
                                    showGCalFrequencyPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGCalFrequencyPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    HorizontalDivider()
}
