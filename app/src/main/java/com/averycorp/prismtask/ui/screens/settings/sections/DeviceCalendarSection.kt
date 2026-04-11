package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.remote.DeviceCalendar
import com.averycorp.prismtask.ui.components.settings.SectionHeader

@Composable
fun DeviceCalendarSection(
    calendarSyncEnabled: Boolean,
    calendarName: String,
    availableCalendars: List<DeviceCalendar>,
    onLoadCalendars: () -> Unit,
    onSelectCalendar: (DeviceCalendar) -> Unit,
    onSetCalendarSyncEnabled: (Boolean) -> Unit
) {
    var showCalendarPicker by remember { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            onLoadCalendars()
            showCalendarPicker = true
        }
    }

    SectionHeader("Device Calendar")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Sync Tasks to Device Calendar", style = MaterialTheme.typography.bodyLarge)
            if (calendarSyncEnabled && calendarName.isNotBlank()) {
                Text(
                    text = calendarName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = calendarSyncEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    if (calendarName.isBlank()) {
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    } else {
                        onSetCalendarSyncEnabled(true)
                    }
                } else {
                    onSetCalendarSyncEnabled(false)
                }
            }
        )
    }

    if (calendarSyncEnabled) {
        OutlinedButton(
            onClick = {
                calendarPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_CALENDAR,
                        android.Manifest.permission.WRITE_CALENDAR
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(if (calendarName.isNotBlank()) "Change Calendar ($calendarName)" else "Select Calendar")
        }
    }

    if (showCalendarPicker && availableCalendars.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            title = { Text("Select Calendar") },
            text = {
                Column {
                    availableCalendars.forEach { cal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCalendar(cal)
                                    onSetCalendarSyncEnabled(true)
                                    showCalendarPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    cal.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    HorizontalDivider()
}
