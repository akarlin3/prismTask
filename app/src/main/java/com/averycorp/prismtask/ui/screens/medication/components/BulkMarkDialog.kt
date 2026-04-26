package com.averycorp.prismtask.ui.screens.medication.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.BulkMarkScope
import com.averycorp.prismtask.ui.screens.medication.MedicationSlotTodayState

/**
 * Bulk-mark medications dialog. The user picks a scope (one slot or
 * the whole day), then picks a target tier from the canonical 4-tier
 * ladder. The summary line surfaces the action's blast radius before
 * the user commits — that's the bulk-action equivalent of
 * [com.averycorp.prismtask.ui.screens.batch.BatchPreviewScreen],
 * scoped to a direct (non-AI) action so we don't route 5 medications
 * through a 5-row preview screen.
 *
 * The dialog is stateless aside from the user's in-progress picks.
 * It does not perform the write — `onConfirm` hands the choices back
 * to [com.averycorp.prismtask.ui.screens.medication.MedicationViewModel.bulkMark]
 * which routes through the PR #772 batch infrastructure for shared
 * `batch_id` + 24h durable history undo.
 */
@Composable
fun BulkMarkDialog(
    slotStates: List<MedicationSlotTodayState>,
    onDismiss: () -> Unit,
    onConfirm: (BulkMarkScope, slotId: Long?, AchievedTier) -> Unit
) {
    val firstSlotId = slotStates.firstOrNull()?.slot?.id
    var scope by remember { mutableStateOf(BulkMarkScope.SLOT) }
    var pickedSlotId by remember { mutableStateOf(firstSlotId) }
    var pickedTier by remember { mutableStateOf<AchievedTier?>(null) }

    val targetCount = when (scope) {
        BulkMarkScope.SLOT -> slotStates.firstOrNull { it.slot.id == pickedSlotId }
            ?.medications?.size ?: 0
        BulkMarkScope.FULL_DAY -> slotStates.sumOf { it.medications.size }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTag = "bulk-mark-dialog" },
        title = { Text("Bulk Mark Medications") },
        text = {
            Column {
                Text(
                    "Scope",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ScopeRadio(
                    label = "This slot",
                    selected = scope == BulkMarkScope.SLOT,
                    onClick = { scope = BulkMarkScope.SLOT }
                )
                ScopeRadio(
                    label = "Full day (all slots)",
                    selected = scope == BulkMarkScope.FULL_DAY,
                    onClick = { scope = BulkMarkScope.FULL_DAY }
                )

                if (scope == BulkMarkScope.SLOT) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Slot",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        items(slotStates, key = { it.slot.id }) { state ->
                            ScopeRadio(
                                label = "${state.slot.name} (${state.medications.size} med${if (state.medications.size == 1) "" else "s"})",
                                selected = pickedSlotId == state.slot.id,
                                onClick = { pickedSlotId = state.slot.id }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Set to",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AchievedTier.entries.forEach { tier ->
                        FilterChip(
                            selected = pickedTier == tier,
                            onClick = { pickedTier = tier },
                            label = { Text(tier.toStorage().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val pickedTierLabel = pickedTier?.toStorage() ?: "…"
                val summary = when (scope) {
                    BulkMarkScope.SLOT -> {
                        val slotName = slotStates.firstOrNull { it.slot.id == pickedSlotId }
                            ?.slot?.name ?: "—"
                        "This will mark $targetCount medication${if (targetCount == 1) "" else "s"} " +
                            "in slot \"$slotName\" as $pickedTierLabel."
                    }
                    BulkMarkScope.FULL_DAY ->
                        "This will mark $targetCount medication${if (targetCount == 1) "" else "s"} " +
                            "across today as $pickedTierLabel."
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pickedTier != null && targetCount > 0,
                onClick = {
                    pickedTier?.let { tier ->
                        val slotIdParam = if (scope == BulkMarkScope.SLOT) pickedSlotId else null
                        onConfirm(scope, slotIdParam, tier)
                    }
                }
            ) { Text("Mark") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScopeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.height(0.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
