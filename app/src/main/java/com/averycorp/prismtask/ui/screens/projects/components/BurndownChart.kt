package com.averycorp.prismtask.ui.screens.projects.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.ProjectBurndown
import java.time.format.DateTimeFormatter

/**
 * Compact burndown chart for a single project. Two lines:
 *  - "Actual remaining" (solid, accent) — `null` for future days, so the
 *    line stops at today.
 *  - "Ideal" (dashed, muted) — straight line from total at start to
 *    zero at end.
 *
 * Caller is responsible for the empty-state path; this composable
 * assumes [burndown] is non-null and contains at least 2 points.
 */
@Composable
fun BurndownChart(
    burndown: ProjectBurndown,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Burndown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${burndown.remainingTasks} of ${burndown.totalTasks} tasks remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = "Velocity ${"%.1f".format(burndown.velocityPerDay)}/d",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    burndown.projectedCompletion?.let { date ->
                        Text(
                            text = "Done ~${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            BurndownCanvas(
                burndown = burndown,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}

@Composable
private fun BurndownCanvas(
    burndown: ProjectBurndown,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val points = burndown.points
    if (points.size < 2) return

    Canvas(modifier = modifier) {
        val maxY = burndown.totalTasks.coerceAtLeast(1).toFloat()
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

        // Ideal line — dashed grey
        val idealPath = Path()
        points.forEachIndexed { i, point ->
            val x = i * stepX
            val y = size.height - (point.idealRemaining.toFloat() / maxY * size.height)
            if (i == 0) idealPath.moveTo(x, y) else idealPath.lineTo(x, y)
        }
        drawPath(
            idealPath,
            color = muted.copy(alpha = 0.6f),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        )

        // Actual line — solid accent, only over the range with non-null actuals
        val actualPath = Path()
        var moveToNext = true
        points.forEachIndexed { i, point ->
            val actual = point.actualRemaining ?: return@forEachIndexed
            val x = i * stepX
            val y = size.height - (actual.toFloat() / maxY * size.height)
            if (moveToNext) {
                actualPath.moveTo(x, y)
                moveToNext = false
            } else {
                actualPath.lineTo(x, y)
            }
        }
        drawPath(
            actualPath,
            color = accent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Today marker — vertical hairline at the boundary between actual and projected
        val lastActualIndex = points.indexOfLast { it.actualRemaining != null }
        if (lastActualIndex >= 0 && lastActualIndex < points.size - 1) {
            val x = lastActualIndex * stepX
            drawLine(
                color = accent.copy(alpha = 0.35f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
