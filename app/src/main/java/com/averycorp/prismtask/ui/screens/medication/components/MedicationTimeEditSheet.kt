package com.averycorp.prismtask.ui.screens.medication.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * Bottom sheet that lets the user stamp a wall-clock time on the slot's
 * tier-state — the primary affordance for logging "I took this at 8am
 * but only just opened the app". Opened via long-press on the slot's
 * tier chip.
 *
 * The time picker defaults to the current intended_time (or the current
 * wall-clock if no user override exists yet). Save composes the picked
 * hour/minute with today's date in the device timezone. Future times
 * are capped to `now` — backdating only, no forward-dating.
 *
 * Cross-day backlogging is intentionally out of scope for v1; long-tail
 * "log yesterday's dose" happens via the medication log screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationTimeEditSheet(
    initialIntendedTime: Long?,
    slotName: String,
    onDismiss: () -> Unit,
    onSave: (intendedTime: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    // Seed from the user's existing intended_time or "now". Keep the full
    // Calendar around so we compose against today's date, not epoch.
    val now = System.currentTimeMillis()
    val seed = initialIntendedTime ?: now
    val seedCal = remember(seed) {
        Calendar.getInstance().apply { timeInMillis = seed }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = seedCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = seedCal.get(Calendar.MINUTE),
        is24Hour = false
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "When did you take it?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = slotName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            TimePicker(state = timePickerState)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Button(onClick = {
                    val edited = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // Cap forward-dating — future times aren't a supported
                    // backlog use case and would corrupt the audit story.
                    val capped = minOf(edited.timeInMillis, System.currentTimeMillis())
                    onSave(capped)
                }) { Text("Save") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
