package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.averycorp.prismtask.ui.components.settings.DurationPickerDialog
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsRowWithSubtitle

@Composable
fun TimerSection(
    timerWorkSeconds: Int,
    timerBreakSeconds: Int,
    timerLongBreakSeconds: Int,
    onTimerWorkMinutesChange: (Int) -> Unit,
    onTimerBreakMinutesChange: (Int) -> Unit,
    onTimerLongBreakMinutesChange: (Int) -> Unit
) {
    var showTimerWorkDialog by remember { mutableStateOf(false) }
    var showTimerBreakDialog by remember { mutableStateOf(false) }
    var showTimerLongBreakDialog by remember { mutableStateOf(false) }

    if (showTimerWorkDialog) {
        DurationPickerDialog(
            title = "Work Duration",
            currentMinutes = timerWorkSeconds / 60,
            onConfirm = {
                onTimerWorkMinutesChange(it)
                showTimerWorkDialog = false
            },
            onDismiss = { showTimerWorkDialog = false }
        )
    }

    if (showTimerBreakDialog) {
        DurationPickerDialog(
            title = "Short Break Duration",
            currentMinutes = timerBreakSeconds / 60,
            onConfirm = {
                onTimerBreakMinutesChange(it)
                showTimerBreakDialog = false
            },
            onDismiss = { showTimerBreakDialog = false }
        )
    }

    if (showTimerLongBreakDialog) {
        DurationPickerDialog(
            title = "Long Break Duration",
            currentMinutes = timerLongBreakSeconds / 60,
            onConfirm = {
                onTimerLongBreakMinutesChange(it)
                showTimerLongBreakDialog = false
            },
            onDismiss = { showTimerLongBreakDialog = false }
        )
    }

    SectionHeader("Timer")

    SettingsRowWithSubtitle(
        title = "Work Duration",
        subtitle = "${timerWorkSeconds / 60} min",
        onClick = { showTimerWorkDialog = true }
    )
    SettingsRowWithSubtitle(
        title = "Short Break Duration",
        subtitle = "${timerBreakSeconds / 60} min",
        onClick = { showTimerBreakDialog = true }
    )
    SettingsRowWithSubtitle(
        title = "Long Break Duration",
        subtitle = "${timerLongBreakSeconds / 60} min",
        onClick = { showTimerLongBreakDialog = true }
    )

    HorizontalDivider()
}
