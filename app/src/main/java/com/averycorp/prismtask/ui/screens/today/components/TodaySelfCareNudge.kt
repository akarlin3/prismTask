package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.SelfCareNudge

/**
 * Gentle self-care nudge card (v1.4.0 V2). Shown beneath the balance bar
 * when SelfCareNudgeEngine picks a nudge for the current moment. The
 * user can tap "Did It" to log a quick self-care completion, "Snooze" to
 * hide for an hour, or "Not Now" to dismiss for the day.
 */
@Composable
internal fun SelfCareNudgeCard(
    nudge: SelfCareNudge,
    onDidIt: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\uD83D\uDCA1",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = nudge.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text("Not Now", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onSnooze) {
                    Text("Snooze", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDidIt) {
                    Text("Did It \u2713", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
