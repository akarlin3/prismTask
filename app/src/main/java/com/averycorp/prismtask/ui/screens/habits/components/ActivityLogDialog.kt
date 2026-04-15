package com.averycorp.prismtask.ui.screens.habits.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.repository.HabitWithStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dialog for logging an ad-hoc habit activity — similar to
 * [HabitLogDialog] but with explicit date selection (Today / Yesterday
 * preset chips + a Material 3 date picker fallback) so users can
 * back-fill completions they missed at the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityLogDialog(
    habitWithStatus: HabitWithStatus,
    onConfirm: (Long, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    var logDate by remember { mutableStateOf(today) }
    var logNotes by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val yesterday = remember {
        val cal = Calendar.getInstance()
        cal.timeInMillis = today
        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.timeInMillis
    }
    val presetDates = remember { setOf(today, yesterday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(habitWithStatus.habit.icon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log ${habitWithStatus.habit.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Date", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today" to today, "Yesterday" to yesterday).forEach { (label, date) ->
                        FilterChip(
                            selected = logDate == date,
                            onClick = { logDate = date },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    FilterChip(
                        selected = logDate !in presetDates,
                        onClick = { showDatePicker = true },
                        label = { Text("Pick Date\u2026", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                Text(
                    text = "Selected: ${dateFormat.format(Date(logDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = logNotes,
                    onValueChange = { logNotes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Went to dentist, all good") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(logDate, logNotes.ifBlank { null }) }) {
                Text("Log Activity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = logDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { logDate = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
