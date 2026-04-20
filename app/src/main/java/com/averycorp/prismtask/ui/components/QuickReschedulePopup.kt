package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.averycorp.prismtask.domain.usecase.DateShortcuts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Quick reschedule popup shown on long-press of a task card. Offers a handful
 * of shortcut dates plus a "Pick Date..." option and a "Plan for Today"
 * affordance. All options resolve through the [onReschedule] / [onPlanForToday]
 * callbacks — this composable does not talk to any ViewModel directly so it
 * can be dropped into any screen that displays task cards.
 *
 * @param hasDueDate controls whether the "Remove Date" row is rendered.
 * @param onReschedule called with the chosen due date (null = remove).
 * @param onPlanForToday pins the task to today without touching dueDate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReschedulePopup(
    hasDueDate: Boolean,
    onDismiss: () -> Unit,
    onReschedule: (Long?) -> Unit,
    onPlanForToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }

    val today = remember(now) { DateShortcuts.today(now) }
    val tomorrow = remember(now) { DateShortcuts.tomorrow(now) }
    val nextMonday = remember(now) { DateShortcuts.nextMonday(now) }
    val nextWeek = remember(now) { DateShortcuts.nextWeek(now) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = today)
        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
                onDismiss()
            },
            confirmButton = {
                TextButton(onClick = {
                    val picked = datePickerToLocalMillis(datePickerState.selectedDateMillis)
                    showDatePicker = false
                    if (picked != null) {
                        onReschedule(picked)
                    }
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    onDismiss()
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
        return
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = modifier
                .width(260.dp)
                .shadow(8.dp, MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                RescheduleRow(
                    emoji = "\u27A1\uFE0F",
                    label = "Move to Tomorrow",
                    trailing = formatShortDate(tomorrow),
                    onClick = {
                        onReschedule(tomorrow)
                        onDismiss()
                    }
                )
                RescheduleRow(
                    emoji = "\uD83D\uDCC5",
                    label = "Today",
                    trailing = formatShortDate(today),
                    onClick = {
                        onReschedule(today)
                        onDismiss()
                    }
                )
                RescheduleRow(
                    emoji = "\uD83D\uDCC5",
                    label = "Next Monday",
                    trailing = formatShortDate(nextMonday),
                    onClick = {
                        onReschedule(nextMonday)
                        onDismiss()
                    }
                )
                RescheduleRow(
                    emoji = "\uD83D\uDCC5",
                    label = "Next Week",
                    trailing = formatShortDate(nextWeek),
                    onClick = {
                        onReschedule(nextWeek)
                        onDismiss()
                    }
                )
                RescheduleRow(
                    emoji = "\uD83D\uDCC5",
                    label = "Pick Date...",
                    trailing = null,
                    onClick = { showDatePicker = true }
                )
                if (hasDueDate) {
                    RescheduleRow(
                        emoji = "\uD83D\uDEAB",
                        label = "Remove Date",
                        trailing = null,
                        onClick = {
                            onReschedule(null)
                            onDismiss()
                        }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                RescheduleRow(
                    emoji = "\uD83D\uDCCC",
                    label = "Plan for Today",
                    trailing = formatShortDate(today),
                    onClick = {
                        onPlanForToday()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun RescheduleRow(
    emoji: String,
    label: String,
    trailing: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val shortDateFormatter by lazy { SimpleDateFormat("MMM d", Locale.getDefault()) }

private fun formatShortDate(millis: Long): String = shortDateFormatter.format(Date(millis))
