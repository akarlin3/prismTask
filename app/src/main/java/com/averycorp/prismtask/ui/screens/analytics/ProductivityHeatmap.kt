package com.averycorp.prismtask.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.DailyScore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * GitHub-style 12-week productivity heatmap. Each cell is one day; cell
 * shade comes from the daily score in four buckets:
 *  - 0–39  → very low (destructive tint)
 *  - 40–59 → low      (warning tint)
 *  - 60–79 → ok       (accent / 0.55 alpha)
 *  - 80+   → great    (accent / full)
 *
 * Tap a cell to see the date + score in the inline tooltip row beneath
 * the grid.
 */
private const val WEEKS = 12
private const val DAYS_PER_WEEK = 7
private const val TOTAL_DAYS = WEEKS * DAYS_PER_WEEK // 84

@Composable
fun ProductivityHeatmap(
    scores: List<DailyScore>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val cellsByDate = remember(scores) { scores.associateBy { it.date } }
    val today = remember(scores) {
        scores.maxByOrNull { it.date.toEpochDay() }?.date ?: LocalDate.now()
    }
    // Calendar-week-aligned start: walk back to a Monday so each
    // column lines up to a single calendar week.
    val gridStart = remember(today) {
        var anchor = today.minusDays(TOTAL_DAYS.toLong() - 1)
        while (anchor.dayOfWeek != DayOfWeek.MONDAY) {
            anchor = anchor.minusDays(1)
        }
        anchor
    }

    var selected by remember { mutableStateOf<HeatmapSelection?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Productivity Heatmap",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Last 12 weeks — score per day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            HeatmapGrid(
                gridStart = gridStart,
                today = today,
                cellsByDate = cellsByDate,
                accent = accent,
                onCellSelected = { selected = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(WEEKS.toFloat() / DAYS_PER_WEEK.toFloat())
            )

            Spacer(modifier = Modifier.height(8.dp))
            HeatmapTooltip(selection = selected)
            Spacer(modifier = Modifier.height(4.dp))
            HeatmapLegend(accent = accent)
        }
    }
}

@Composable
private fun HeatmapGrid(
    gridStart: LocalDate,
    today: LocalDate,
    cellsByDate: Map<LocalDate, DailyScore>,
    accent: Color,
    onCellSelected: (HeatmapSelection?) -> Unit,
    modifier: Modifier = Modifier
) {
    val emptyCellColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier.pointerInput(gridStart, today, cellsByDate) {
            // The cell layout is a 12 (cols) × 7 (rows) grid spanning the
            // full canvas. Map a tap back into a (col, row) index, then
            // resolve the date from gridStart.
            // Using awaitPointerEventScope keeps the gesture detection
            // simple — we only need taps, not drags.
            // Note: avoid `detectTapGestures` — its onTap callback is
            // called outside the layout pass, which means we'd need to
            // remember the last layout size separately.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val first = event.changes.firstOrNull() ?: continue
                    if (!first.pressed) continue
                    val cellWidth = size.width.toFloat() / WEEKS
                    val cellHeight = size.height.toFloat() / DAYS_PER_WEEK
                    val col = (first.position.x / cellWidth).toInt().coerceIn(0, WEEKS - 1)
                    val row = (first.position.y / cellHeight).toInt().coerceIn(0, DAYS_PER_WEEK - 1)
                    val date = gridStart.plusDays((col * DAYS_PER_WEEK + row).toLong())
                    if (date.isAfter(today)) {
                        onCellSelected(null)
                    } else {
                        val score = cellsByDate[date]?.score
                        onCellSelected(HeatmapSelection(date = date, score = score))
                    }
                    first.consume()
                }
            }
        }
    ) {
        val cellW = size.width / WEEKS
        val cellH = size.height / DAYS_PER_WEEK
        val pad = 2f

        for (col in 0 until WEEKS) {
            for (row in 0 until DAYS_PER_WEEK) {
                val date = gridStart.plusDays((col * DAYS_PER_WEEK + row).toLong())
                if (date.isAfter(today)) continue
                val score = cellsByDate[date]?.score
                val color = if (score == null) {
                    emptyCellColor
                } else {
                    bucketColorForScore(score = score, accent = accent, emptyTint = emptyCellColor)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(col * cellW + pad, row * cellH + pad),
                    size = Size(cellW - 2 * pad, cellH - 2 * pad)
                )
            }
        }
    }
}

private fun bucketColorForScore(score: Double, accent: Color, emptyTint: Color): Color = when {
    score >= 80.0 -> accent
    score >= 60.0 -> accent.copy(alpha = 0.55f)
    score >= 40.0 -> accent.copy(alpha = 0.30f)
    else -> emptyTint
}

private data class HeatmapSelection(val date: LocalDate, val score: Double?)

@Composable
private fun HeatmapTooltip(selection: HeatmapSelection?) {
    val text = if (selection == null) {
        "Tap a cell to see the date and score."
    } else {
        val datePart = selection.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        val scorePart = selection.score?.let { "${it.toInt()}/100" } ?: "no data"
        "$datePart · $scorePart"
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HeatmapLegend(accent: Color) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(empty, accent.copy(alpha = 0.30f), accent.copy(alpha = 0.55f), accent).forEach { tint ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(color = tint, modifier = Modifier.size(10.dp)) {}
                }
            }
        }
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
