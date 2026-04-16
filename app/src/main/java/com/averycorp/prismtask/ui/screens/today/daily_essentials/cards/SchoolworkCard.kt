package com.averycorp.prismtask.ui.screens.today.daily_essentials.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.AssignmentSummary
import com.averycorp.prismtask.domain.usecase.SchoolworkCardState

@Composable
fun SchoolworkCard(
    state: SchoolworkCardState,
    onToggleHabit: () -> Unit,
    onOpenAssignment: (assignmentId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFCFB87C)
    val description = buildString {
        append("Schoolwork")
        state.habit?.let {
            append(", habit ${if (it.completedToday) "done" else "not done"}")
        }
        if (state.assignmentsDueToday.isNotEmpty()) {
            append(", ${state.assignmentsDueToday.size} assignments due today")
        }
    }

    DailyEssentialCard(
        accent = accent,
        contentDescription = description,
        onClick = null,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Schoolwork",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            state.habit?.let { habit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(onClick = onToggleHabit)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (habit.completedToday) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (habit.completedToday) TextDecoration.LineThrough else null
                    )
                }
            }

            state.assignmentsDueToday.forEach { assignment ->
                AssignmentRow(
                    summary = assignment,
                    onClick = { onOpenAssignment(assignment.id) }
                )
            }
        }
    }
}

@Composable
private fun AssignmentRow(
    summary: AssignmentSummary,
    onClick: () -> Unit
) {
    val dot = if (summary.courseColor != 0) Color(summary.courseColor) else Color.Gray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (summary.completed) TextDecoration.LineThrough else null,
                color = if (summary.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (summary.courseName.isNotBlank()) {
                Text(
                    text = summary.courseName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
