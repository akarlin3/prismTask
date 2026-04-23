package com.averycorp.prismtask.ui.screens.medication.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.medication.MedicationTier

/**
 * Reusable tier radio for the medication create / edit flow. Replaces the
 * existing dropdown picker (which uses lowercase string ids from
 * `SelfCareRoutines.medicationTiers`) with an explicit three-option
 * radio backed by [MedicationTier].
 *
 * Will be wired into MedDialog as part of the PR3 rewire (the rewire
 * also switches the dialog's storage type from `SelfCareStepEntity` to
 * [com.averycorp.prismtask.data.local.entity.MedicationEntity], which is
 * the shape that carries this enum on its `tier` column).
 */
@Composable
fun MedicationTierRadio(
    selected: MedicationTier,
    onSelected: (MedicationTier) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Tier",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MedicationTier.LADDER.forEach { tier ->
            val isSelected = tier == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ).border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(10.dp)
                    ).clickable { onSelected(tier) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelected(tier) },
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = tierLabel(tier),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tierDescription(tier),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun tierLabel(tier: MedicationTier): String = when (tier) {
    MedicationTier.ESSENTIAL -> "Essential"
    MedicationTier.PRESCRIPTION -> "Prescription"
    MedicationTier.COMPLETE -> "Complete"
}

private fun tierDescription(tier: MedicationTier): String = when (tier) {
    MedicationTier.ESSENTIAL -> "Cannot miss — flagged in red if skipped."
    MedicationTier.PRESCRIPTION -> "Should take — flagged in yellow if skipped."
    MedicationTier.COMPLETE -> "Optional — counts toward a complete day."
}
