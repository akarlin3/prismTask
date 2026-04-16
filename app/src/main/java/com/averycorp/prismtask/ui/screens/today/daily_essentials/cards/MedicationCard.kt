package com.averycorp.prismtask.ui.screens.today.daily_essentials.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.MedicationCardState

@Composable
fun MedicationCard(
    state: MedicationCardState,
    onMarkTaken: () -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.nextDose.displayLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (state.totalDueToday == 1) {
                            "1 dose due"
                        } else {
                            "${state.totalDueToday} doses due"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onMarkTaken) { Text("Mark Taken") }
            }
            if (state.otherDoses.isNotEmpty()) {
                Text(
                    text = state.otherDoses.joinToString(", ") { it.displayLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
