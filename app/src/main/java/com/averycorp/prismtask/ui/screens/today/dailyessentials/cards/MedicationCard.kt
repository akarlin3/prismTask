package com.averycorp.prismtask.ui.screens.today.dailyessentials.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.MedicationCardState
import com.averycorp.prismtask.domain.usecase.MedicationSlot
import com.averycorp.prismtask.domain.usecase.MedicationSlotGrouper

/**
 * Medication card for Daily Essentials. Renders one row per distinct time
 * slot, e.g. ``"08:00 meds: Lipitor, Metformin, Vitamin D"``. Tapping the
 * row opens [MedicationSlotBottomSheet] (owned by the caller); the trailing
 * checkbox batch-marks every med in the slot.
 */
@Composable
fun MedicationCard(
    state: MedicationCardState,
    onToggleSlot: (MedicationSlot, Boolean) -> Unit,
    onOpenSlot: (MedicationSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFEF4444)
    val description =
        "Medication — ${state.totalDueToday} dose${if (state.totalDueToday == 1) "" else "s"} due"

    DailyEssentialCard(
        accent = accent,
        contentDescription = description,
        onClick = null,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\uD83D\uDC8A",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (state.totalDueToday == 1) "1 dose due" else "${state.totalDueToday} doses due",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.slots.forEach { slot ->
                MedicationSlotRow(
                    slot = slot,
                    onToggle = { checked -> onToggleSlot(slot, checked) },
                    onOpen = { onOpenSlot(slot) }
                )
            }
        }
    }
}

@Composable
internal fun MedicationSlotRow(
    slot: MedicationSlot,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = MedicationSlotGrouper.rowLabel(slot)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = slot.isTaken,
            onCheckedChange = onToggle
        )
    }
}
