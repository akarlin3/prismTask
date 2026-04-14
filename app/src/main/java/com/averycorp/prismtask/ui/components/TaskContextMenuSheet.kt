package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet shown when the user long-presses a task card. Surfaces a
 * small list of task-level actions that are common across every screen
 * (Today, Task List, Week / Month / Timeline) so long-press behavior is
 * consistent everywhere.
 *
 * Each action is invoked through the supplied callback; the caller is
 * responsible for triggering downstream UI (e.g. opening the move-to-
 * project sheet or the reschedule popup). This sheet only dismisses —
 * it does not manage its follow-up flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskContextMenuSheet(
    taskTitle: String,
    onDismiss: () -> Unit,
    onReschedule: () -> Unit,
    onMoveToProject: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = taskTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            ContextMenuRow(
                emoji = "\u23F0",
                label = "Reschedule",
                onClick = onReschedule
            )
            ContextMenuRow(
                emoji = "\uD83D\uDCC1",
                label = "Move To Project",
                onClick = onMoveToProject
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ContextMenuRow(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
