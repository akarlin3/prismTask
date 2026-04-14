package com.averycorp.prismtask.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.averycorp.prismtask.domain.model.SwipeAction

/**
 * Visual attributes for a swipe gesture background. Returned from
 * [swipeActionStyle] and used by task/habit cards when rendering the
 * swipe-to-action background.
 */
data class SwipeActionStyle(
    val backgroundColor: Color,
    val icon: ImageVector?,
    val label: String
)

/**
 * Returns the background color, icon, and label associated with the given
 * [SwipeAction]. Colors match those referenced in the v1.3.0 customizability
 * spec: COMPLETE=green, DELETE=red, RESCHEDULE=blue, ARCHIVE=gray,
 * MOVE_TO_PROJECT=purple, FLAG=orange, NONE=transparent.
 */
fun swipeActionStyle(action: SwipeAction): SwipeActionStyle = when (action) {
    SwipeAction.COMPLETE -> SwipeActionStyle(Color(0xFF4CAF50), Icons.Default.Check, "Complete")
    SwipeAction.DELETE -> SwipeActionStyle(Color(0xFFE53935), Icons.Default.Delete, "Delete")
    SwipeAction.RESCHEDULE -> SwipeActionStyle(Color(0xFF5C8CC7), Icons.AutoMirrored.Filled.ArrowForward, "Reschedule")
    SwipeAction.ARCHIVE -> SwipeActionStyle(Color(0xFF757575), Icons.Default.Archive, "Archive")
    SwipeAction.MOVE_TO_PROJECT -> SwipeActionStyle(Color(0xFF9C27B0), Icons.Default.FolderOpen, "Move")
    SwipeAction.FLAG -> SwipeActionStyle(Color(0xFFFF9800), Icons.Default.Flag, "Flag")
    SwipeAction.NONE -> SwipeActionStyle(Color.Transparent, null, "")
}

/**
 * Dispatches [action] to the supplied handlers. The `onShowPicker` callback is
 * invoked for actions that need a picker (RESCHEDULE, MOVE_TO_PROJECT).
 * Returns true when the swipe should be considered consumed (i.e. the row
 * should animate away).
 */
fun dispatchSwipeAction(
    action: SwipeAction,
    taskId: Long,
    onComplete: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReschedule: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onMoveToProject: (Long) -> Unit,
    onToggleFlag: (Long) -> Unit
): Boolean = when (action) {
    SwipeAction.COMPLETE -> {
        onComplete(taskId)
        true
    }
    SwipeAction.DELETE -> {
        onDelete(taskId)
        true
    }
    SwipeAction.RESCHEDULE -> {
        onReschedule(taskId)
        true
    }
    SwipeAction.ARCHIVE -> {
        onArchive(taskId)
        true
    }
    SwipeAction.MOVE_TO_PROJECT -> {
        onMoveToProject(taskId)
        true
    }
    SwipeAction.FLAG -> {
        onToggleFlag(taskId)
        false // flag toggle shouldn't dismiss row
    }
    SwipeAction.NONE -> false
}
