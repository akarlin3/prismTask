package com.averycorp.prismtask.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.ProductivityRange
import com.averycorp.prismtask.domain.model.TimeTrackingResponse

/**
 * Time-tracking bar-chart section on `TaskAnalyticsScreen` — header + range
 * selector + Compose Canvas bar chart of minutes-per-day. Renders only on
 * Pro tier (the parent screen gates the call site). Empty days within the
 * window draw as zero-height bars so the time axis stays continuous.
 */
@Composable
fun TimeTrackingSection(
    response: TimeTrackingResponse,
    selectedRange: ProductivityRange,
    onRangeSelected: (ProductivityRange) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TimeTrackingHeader(response = response)
            Spacer(modifier = Modifier.height(8.dp))
            TimeTrackingRangeSelector(
                selected = selectedRange,
                onSelected = onRangeSelected,
                accent = accent
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (response.totalMinutes > 0) {
                TimeTrackingBarChart(
                    response = response,
                    accent = accent,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No time logged in this window. Add an entry from the Schedule tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeTrackingHeader(response: TimeTrackingResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Time Tracking",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = totalsLabel(response),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (response.activeDayCount > 0) {
            Text(
                text = "${response.activeDayCount} active days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeTrackingRangeSelector(
    selected: ProductivityRange,
    onSelected: (ProductivityRange) -> Unit,
    accent: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ProductivityRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelected(range) },
                label = { Text(range.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun TimeTrackingBarChart(
    response: TimeTrackingResponse,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val maxMinutes = response.buckets.maxOfOrNull { it.totalMinutes } ?: 0
    Canvas(modifier = modifier) {
        if (response.buckets.isEmpty() || maxMinutes <= 0) return@Canvas

        val pad = 6.dp.toPx()
        val gapBetweenBars = 2.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val totalGap = gapBetweenBars * (response.buckets.size - 1).coerceAtLeast(0)
        val barWidth = ((w - totalGap) / response.buckets.size).coerceAtLeast(1f)

        response.buckets.forEachIndexed { i, bucket ->
            if (bucket.totalMinutes <= 0) return@forEachIndexed
            val barHeight = (bucket.totalMinutes.toFloat() / maxMinutes) * h
            val x = pad + i * (barWidth + gapBetweenBars)
            val y = pad + h - barHeight
            drawRect(
                color = accent,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

private fun totalsLabel(response: TimeTrackingResponse): String {
    val totalHours = response.totalMinutes / 60
    val remainderMinutes = response.totalMinutes % 60
    val totalLabel = when {
        response.totalMinutes == 0 -> "0 min"
        totalHours == 0 -> "$remainderMinutes min"
        remainderMinutes == 0 -> "${totalHours}h"
        else -> "${totalHours}h ${remainderMinutes}m"
    }
    val avgLabel = if (response.activeDayCount > 0) {
        " · avg ${response.averageMinutesPerActiveDay} min/day"
    } else {
        ""
    }
    return "Total $totalLabel$avgLabel"
}
