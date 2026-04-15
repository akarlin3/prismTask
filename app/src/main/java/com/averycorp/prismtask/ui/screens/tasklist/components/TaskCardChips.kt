package com.averycorp.prismtask.ui.screens.tasklist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.ui.theme.LocalPriorityColors

/**
 * Small decorative chips and badges used inside [TaskItem]. Kept in
 * a dedicated file so the card itself stays focused on layout rather
 * than color parsing and chip styling.
 */

/**
 * Solid 10dp circle in the task's priority color.
 */
@Composable
internal fun PriorityDot(priority: Int) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(LocalPriorityColors.current.forLevel(priority))
    )
}

/**
 * Eisenhower-matrix quadrant badge: a soft-colored circle containing
 * the quadrant number (1–4). Returns empty content for unknown values.
 */
@Composable
internal fun EisenhowerBadge(quadrant: String) {
    val (color, label) = when (quadrant) {
        "Q1" -> Color(0xFFEF4444) to "1"
        "Q2" -> Color(0xFF3B82F6) to "2"
        "Q3" -> Color(0xFFF59E0B) to "3"
        "Q4" -> Color(0xFF6B7280) to "4"
        else -> return
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 8.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            lineHeight = 8.sp
        )
    }
}

/**
 * Compact tag chip: colored dot + tag name, tinted with the tag's
 * stored hex color.
 */
@Composable
internal fun TagChip(tag: TagEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(tag.color))
    } catch (_: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(chipColor)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp
        )
    }
}

/**
 * Compact project chip: project name rendered in the project's
 * stored hex color against a matching soft-tinted background.
 */
@Composable
internal fun ProjectChip(project: ProjectEntity) {
    val chipColor = try {
        Color(android.graphics.Color.parseColor(project.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
