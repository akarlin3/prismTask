package com.averycorp.prismtask.ui.screens.today.dailyessentials.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.MedicationDose
import com.averycorp.prismtask.domain.usecase.MedicationSlot

/**
 * Modal bottom sheet that lists every medication in a given time slot with
 * per-dose checkboxes so the user can mark individual doses taken.
 *
 * ``onToggleDose`` is separate from ``onToggleSlot`` on the row so the caller
 * can route individual toggles to their native completion logs (e.g. a habit
 * completion vs. a self-care step toggle) instead of treating them all as
 * slot-level writes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationSlotBottomSheet(
    slot: MedicationSlot,
    onDismiss: () -> Unit,
    onToggleDose: (MedicationDose, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${slot.displayTime} Meds",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (slot.doses.size == 1) {
                    "1 medication scheduled for this slot"
                } else {
                    "${slot.doses.size} medications scheduled for this slot"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            slot.doses.forEach { dose ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dose.displayLabel.ifBlank { dose.medicationName },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = dose.takenToday,
                        onCheckedChange = { checked -> onToggleDose(dose, checked) }
                    )
                }
            }
        }
    }
}
