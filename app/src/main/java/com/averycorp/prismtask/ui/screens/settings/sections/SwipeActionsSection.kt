package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.preferences.SwipePrefs
import com.averycorp.prismtask.domain.model.SwipeAction
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.swipeActionStyle

@Composable
fun SwipeActionsSection(
    swipePrefs: SwipePrefs,
    onSwipeRightChange: (SwipeAction) -> Unit,
    onSwipeLeftChange: (SwipeAction) -> Unit
) {
    SectionHeader("Swipe Actions")

    Text(
        text = "Customize what happens when you swipe a task card.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    val swipeActionOptions = listOf(
        SwipeAction.COMPLETE to "Complete",
        SwipeAction.DELETE to "Delete",
        SwipeAction.RESCHEDULE to "Reschedule",
        SwipeAction.ARCHIVE to "Archive",
        SwipeAction.MOVE_TO_PROJECT to "Move to Project",
        SwipeAction.FLAG to "Flag",
        SwipeAction.NONE to "None (disabled)"
    )

    var swipeRightExpanded by remember { mutableStateOf(false) }
    var swipeLeftExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { swipeRightExpanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            val style = swipeActionStyle(swipePrefs.right)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(style.backgroundColor)
            )
            Spacer(Modifier.width(8.dp))
            Text("Swipe Right: ${swipeActionOptions.first { it.first == swipePrefs.right }.second}")
        }
        DropdownMenu(
            expanded = swipeRightExpanded,
            onDismissRequest = { swipeRightExpanded = false }
        ) {
            swipeActionOptions.forEach { (action, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(swipeActionStyle(action).backgroundColor)
                        )
                    },
                    onClick = {
                        onSwipeRightChange(action)
                        swipeRightExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { swipeLeftExpanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            val style = swipeActionStyle(swipePrefs.left)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(style.backgroundColor)
            )
            Spacer(Modifier.width(8.dp))
            Text("Swipe Left: ${swipeActionOptions.first { it.first == swipePrefs.left }.second}")
        }
        DropdownMenu(
            expanded = swipeLeftExpanded,
            onDismissRequest = { swipeLeftExpanded = false }
        ) {
            swipeActionOptions.forEach { (action, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(swipeActionStyle(action).backgroundColor)
                        )
                    },
                    onClick = {
                        onSwipeLeftChange(action)
                        swipeLeftExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
}
