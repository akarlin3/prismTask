package com.averycorp.prismtask.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.AnalyticsSummary
import com.averycorp.prismtask.domain.model.WeekTrend

/**
 * Four-tile summary row that mirrors the web PR #715 SummaryTile section
 * (web/src/features/analytics/AnalyticsScreen.tsx lines 271-301). Each tile
 * is a small label + bold value + optional sub-line.
 */
@Composable
fun AnalyticsSummaryTiles(
    summary: AnalyticsSummary,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "Today",
                value = summary.today.completed.toString(),
                sub = "${summary.today.completed} Done · ${summary.today.remaining} Left",
                icon = Icons.Filled.Today,
                accent = accent
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "This Week",
                value = summary.thisWeek.completed.toString(),
                subSlot = { TrendBadge(summary.thisWeek.trend) },
                icon = Icons.Outlined.CalendarMonth,
                accent = accent
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "Streak",
                value = "${summary.streaks.currentDays}d",
                sub = "Longest ${summary.streaks.longestDays}d",
                icon = Icons.Filled.LocalFireDepartment,
                accent = accent
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "Habits (7d)",
                value = "${(summary.habits.completionRate7d * 100).toInt()}%",
                sub = "30d: ${(summary.habits.completionRate30d * 100).toInt()}%",
                icon = Icons.Filled.SelfImprovement,
                accent = accent
            )
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    subSlot: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (subSlot != null) {
                subSlot()
            }
        }
    }
}

/**
 * Free-tier upsell shown in place of [AnalyticsSummaryTiles] when the user is
 * not on Pro. Single card with brief copy; no upgrade-flow wiring (ProUpgrade
 * surfaces are reached from Settings → Pro).
 */
@Composable
fun AnalyticsSummaryProUpsell(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Unlock Productivity Summary",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Today, week trend, streak, and habit completion at a glance — included with Pro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrendBadge(trend: WeekTrend) {
    val (icon, tint, label) = when (trend) {
        WeekTrend.IMPROVING -> Triple(Icons.Filled.TrendingUp, Color(0xFF2E7D32), "Improving")
        WeekTrend.DECLINING -> Triple(Icons.Filled.TrendingDown, Color(0xFFC62828), "Declining")
        WeekTrend.STABLE -> Triple(
            Icons.Filled.TrendingFlat,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Stable"
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
