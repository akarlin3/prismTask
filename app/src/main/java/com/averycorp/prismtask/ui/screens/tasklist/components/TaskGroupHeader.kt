package com.averycorp.prismtask.ui.screens.tasklist.components

import android.content.ClipDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.ui.theme.LocalPrismAttrs
import com.averycorp.prismtask.ui.theme.LocalPrismColors

/**
 * Per-theme section header shown above a group of tasks (e.g. "Today",
 * "Tomorrow", "From Earlier").
 *
 * - Matrix:  `# from_earlier [count]` in lowercase monospace
 * - Void:    short decorative line before the label + no trailing divider
 * - Others:  label in primary color + count + trailing divider hairline
 */
@Composable
internal fun GroupHeader(group: String, count: Int) {
    val displayGroup = if (group == "Overdue") "From Earlier" else group
    val prismColors = LocalPrismColors.current
    val attrs = LocalPrismAttrs.current

    val accentColor = when (group) {
        "Today" -> prismColors.warningColor
        else -> prismColors.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (attrs.editorial) 20.dp else 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (attrs.editorial) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(1.dp)
                    .background(prismColors.onSurface)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = when {
                attrs.terminal -> "# ${displayGroup.lowercase()} [$count]"
                else -> displayGroup
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (attrs.editorial) 2.sp else 1.4.sp,
            color = accentColor
        )
        if (!attrs.terminal) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (attrs.terminal) "" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(prismColors.border)
            )
        }
    }
}

/**
 * Header row for a project section in the By Project view. Shows the
 * project's colored dot + name + task count, and acts as a drop target
 * for the cross-project drag-to-move interaction: any task card dropped
 * onto the header is reassigned to this project (or "No Project" when
 * [project] is null).
 *
 * While a drag is hovering over the header the row scales up slightly
 * and draws an accent border so the drop target is unambiguous. The
 * drag payload is a plain-text ClipData carrying the task id as a long.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectGroupHeader(
    project: ProjectEntity?,
    taskCount: Int,
    onDropTask: (Long) -> Unit
) {
    val accent = if (project != null) {
        try {
            Color(android.graphics.Color.parseColor(project.color))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.primary
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1f,
        label = "projectHeaderScale"
    )

    val target = remember(project?.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isHovered = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isHovered = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isHovered = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipItem = event
                    .toAndroidDragEvent()
                    .clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                val droppedId = clipItem?.text?.toString()?.toLongOrNull()
                isHovered = false
                return if (droppedId != null) {
                    onDropTask(droppedId)
                    true
                } else {
                    false
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isHovered) {
                    Modifier.border(
                        width = 2.dp,
                        color = accent,
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            ).background(
                if (isHovered) accent.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ).dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = target
            ).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = project?.name ?: "No Project",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$taskCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
