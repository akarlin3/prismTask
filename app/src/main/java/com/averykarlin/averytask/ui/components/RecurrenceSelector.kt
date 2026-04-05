package com.averykarlin.averytask.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averykarlin.averytask.domain.model.RecurrenceRule
import com.averykarlin.averytask.domain.model.RecurrenceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurrenceSelector(
    currentRule: RecurrenceRule?,
    onRuleChanged: (RecurrenceRule?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val displayText = when (currentRule?.type) {
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.WEEKLY -> "Weekly"
        RecurrenceType.MONTHLY -> "Monthly"
        RecurrenceType.YEARLY -> "Yearly"
        RecurrenceType.CUSTOM -> "Custom"
        null -> "None"
    }

    // Tappable row
    TextButton(onClick = { showDialog = true }) {
        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Repeat: $displayText")
    }

    if (showDialog) {
        RecurrenceDialog(
            initialRule = currentRule,
            onDismiss = { showDialog = false },
            onConfirm = { rule ->
                onRuleChanged(rule)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceDialog(
    initialRule: RecurrenceRule?,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule?) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialRule?.type) }
    var interval by remember { mutableIntStateOf(initialRule?.interval ?: 1) }
    var intervalText by remember { mutableStateOf((initialRule?.interval ?: 1).toString()) }

    val currentDayOfWeek = remember {
        Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let { dow ->
            // Convert Calendar.DAY_OF_WEEK (Sun=1..Sat=7) to ISO (Mon=1..Sun=7)
            if (dow == Calendar.SUNDAY) 7 else dow - 1
        }
    }
    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }

    var daysOfWeek by remember {
        mutableStateOf(initialRule?.daysOfWeek ?: listOf(currentDayOfWeek))
    }
    var dayOfMonth by remember {
        mutableIntStateOf(initialRule?.dayOfMonth ?: currentDayOfMonth)
    }
    var dayOfMonthText by remember {
        mutableStateOf((initialRule?.dayOfMonth ?: currentDayOfMonth).toString())
    }

    // End condition: 0 = forever, 1 = until date, 2 = after N times
    var endCondition by remember {
        mutableIntStateOf(
            when {
                initialRule?.endDate != null -> 1
                initialRule?.maxOccurrences != null -> 2
                else -> 0
            }
        )
    }
    var endDate by remember { mutableStateOf(initialRule?.endDate) }
    var maxOccurrences by remember { mutableIntStateOf(initialRule?.maxOccurrences ?: 10) }
    var maxOccurrencesText by remember {
        mutableStateOf((initialRule?.maxOccurrences ?: 10).toString())
    }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (selectedType != null) {
                Button(onClick = {
                    val rule = RecurrenceRule(
                        type = selectedType!!,
                        interval = interval.coerceAtLeast(1),
                        daysOfWeek = if (selectedType == RecurrenceType.WEEKLY) daysOfWeek.ifEmpty { listOf(currentDayOfWeek) } else null,
                        dayOfMonth = if (selectedType == RecurrenceType.MONTHLY) dayOfMonth.coerceIn(1, 31) else null,
                        endDate = if (endCondition == 1) endDate else null,
                        maxOccurrences = if (endCondition == 2) maxOccurrences.coerceAtLeast(1) else null
                    )
                    onConfirm(rule)
                }) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Recurrence") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Type selector
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = listOf(
                        null to "None",
                        RecurrenceType.DAILY to "Daily",
                        RecurrenceType.WEEKLY to "Weekly",
                        RecurrenceType.MONTHLY to "Monthly",
                        RecurrenceType.YEARLY to "Yearly"
                    )
                    types.forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                if (type == null) {
                                    onConfirm(null)
                                } else {
                                    selectedType = type
                                }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (selectedType != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Interval
                    val unitLabel = when (selectedType) {
                        RecurrenceType.DAILY -> "days"
                        RecurrenceType.WEEKLY -> "weeks"
                        RecurrenceType.MONTHLY -> "months"
                        RecurrenceType.YEARLY -> "years"
                        else -> "days"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every")
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { v ->
                                intervalText = v
                                v.toIntOrNull()?.let { interval = it }
                            },
                            modifier = Modifier.width(72.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(unitLabel)
                    }

                    // Weekly: day-of-week chips
                    if (selectedType == RecurrenceType.WEEKLY) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("On days:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            dayLabels.forEachIndexed { index, label ->
                                val dayValue = index + 1 // 1=Mon..7=Sun
                                FilterChip(
                                    selected = dayValue in daysOfWeek,
                                    onClick = {
                                        daysOfWeek = if (dayValue in daysOfWeek) {
                                            daysOfWeek - dayValue
                                        } else {
                                            (daysOfWeek + dayValue).sorted()
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    // Monthly: day of month
                    if (selectedType == RecurrenceType.MONTHLY) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("On day")
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = dayOfMonthText,
                                onValueChange = { v ->
                                    dayOfMonthText = v
                                    v.toIntOrNull()?.let { dayOfMonth = it }
                                },
                                modifier = Modifier.width(72.dp),
                                singleLine = true
                            )
                        }
                    }

                    // End condition
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ends:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Forever
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = endCondition == 0,
                            onClick = { endCondition = 0 }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Forever")
                    }

                    // Until date
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = endCondition == 1,
                            onClick = { endCondition = 1 }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Until date")
                        if (endCondition == 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showEndDatePicker = true }) {
                                val dateText = endDate?.let {
                                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                        .format(Date(it))
                                } ?: "Pick date"
                                Text(dateText)
                            }
                        }
                    }

                    // After N times
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = endCondition == 2,
                            onClick = { endCondition = 2 }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("After")
                        if (endCondition == 2) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = maxOccurrencesText,
                                onValueChange = { v ->
                                    maxOccurrencesText = v
                                    v.toIntOrNull()?.let { maxOccurrences = it }
                                },
                                modifier = Modifier.width(72.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("times")
                        }
                    }
                }
            }
        }
    )

    // End date picker
    if (showEndDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = state.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
