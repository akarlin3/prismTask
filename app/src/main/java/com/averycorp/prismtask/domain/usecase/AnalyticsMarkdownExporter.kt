package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.preferences.ProductiveStreakSnapshot
import com.averycorp.prismtask.data.repository.TaskCompletionStats
import com.averycorp.prismtask.domain.model.AnalyticsSummary
import com.averycorp.prismtask.domain.model.ProductivityScoreResponse
import com.averycorp.prismtask.domain.model.ProductivityTrend
import com.averycorp.prismtask.domain.model.TimeTrackingResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builds a self-contained markdown report of the user's analytics state
 * for the Android share sheet. Pure function — no IO, no side effects —
 * so the formatter is easy to test against fixture data.
 *
 * Sections, in order:
 *  1. Header (date range, generated-on stamp)
 *  2. Average score + trend
 *  3. Productive-day streak
 *  4. Task stats (completed, on-time, streaks, completion rate)
 *  5. Time tracking breakdown (top sources)
 *  6. Habit completion
 *  7. Top insights (best/worst day)
 */
@Singleton
class AnalyticsMarkdownExporter @Inject constructor() {

    fun build(
        generatedOn: LocalDate,
        rangeDays: Int,
        productivity: ProductivityScoreResponse?,
        timeTracking: TimeTrackingResponse?,
        stats: TaskCompletionStats?,
        summary: AnalyticsSummary?,
        streak: ProductiveStreakSnapshot?
    ): String = buildString {
        appendLine("# PrismTask Analytics Report")
        appendLine()
        appendLine("- Generated: ${generatedOn.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
        appendLine("- Range: last $rangeDays days")
        appendLine()

        if (productivity != null && productivity.scores.isNotEmpty()) {
            appendLine("## Productivity Score")
            appendLine()
            appendLine("- Average: **${productivity.averageScore.roundToInt()}/100**")
            appendLine("- Trend: ${productivity.trend.label()}")
            productivity.bestDay?.let {
                appendLine("- Best Day: ${it.date.format(DateTimeFormatter.ISO_LOCAL_DATE)} — ${it.score.roundToInt()}/100")
            }
            productivity.worstDay?.let {
                appendLine("- Worst Day: ${it.date.format(DateTimeFormatter.ISO_LOCAL_DATE)} — ${it.score.roundToInt()}/100")
            }
            appendLine()
        }

        if (streak != null && streak.hasAnyHistory) {
            appendLine("## Productive-Day Streak")
            appendLine()
            appendLine("- Current run: ${streak.currentDays} day(s)")
            appendLine("- Longest run: ${streak.longestDays} day(s)")
            streak.lastProductiveDate?.let {
                appendLine("- Last productive day: ${it.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            }
            appendLine()
        }

        if (stats != null) {
            appendLine("## Task Stats")
            appendLine()
            appendLine("- Total completed: ${stats.totalCompleted}")
            stats.avgDaysToComplete?.let {
                appendLine("- Avg days to complete: ${"%.1f".format(it)}")
            }
            stats.overdueRate?.let {
                appendLine("- On-time rate: ${"%.0f".format(100.0 - it)}%")
            }
            appendLine("- Completion rate (7d): ${"%.1f".format(stats.completionRate7Day)}/day")
            appendLine("- Completion rate (30d): ${"%.1f".format(stats.completionRate30Day)}/day")
            if (stats.currentStreak > 0) appendLine("- Current task streak: ${stats.currentStreak} day(s)")
            if (stats.longestStreak > 0) appendLine("- Longest task streak: ${stats.longestStreak} day(s)")
            stats.bestDay?.let {
                appendLine("- Most productive day of week: ${it.name.titleCase()}")
            }
            stats.worstDay?.let {
                appendLine("- Least productive day of week: ${it.name.titleCase()}")
            }
            stats.peakHour?.let {
                appendLine("- Peak hour: ${formatHour(it)}")
            }
            appendLine()
        }

        if (timeTracking != null && timeTracking.totalMinutes > 0) {
            appendLine("## Time Tracked")
            appendLine()
            appendLine("- Total: ${formatDurationMinutes(timeTracking.totalMinutes)}")
            appendLine("- Average per active day: ${formatDurationMinutes(timeTracking.averageMinutesPerActiveDay)}")
            appendLine("- Active days: ${timeTracking.activeDayCount}")
            appendLine()
        }

        if (summary != null) {
            appendLine("## Today")
            appendLine()
            appendLine("- Completed: ${summary.today.completed}")
            appendLine("- Remaining: ${summary.today.remaining}")
            appendLine()
            appendLine("## Habits (7d)")
            appendLine()
            appendLine("- Completion rate: ${"%.0f".format(summary.habits.completionRate7d * 100)}%")
            appendLine("- Active habits: ${summary.habits.activeHabits}")
            appendLine()
        }

        appendLine("---")
        appendLine("Exported from PrismTask.")
    }

    private fun ProductivityTrend.label(): String = when (this) {
        ProductivityTrend.IMPROVING -> "Improving"
        ProductivityTrend.DECLINING -> "Declining"
        ProductivityTrend.STABLE -> "Stable"
    }

    private fun String.titleCase(): String =
        lowercase().replaceFirstChar { it.uppercase() }

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12:00 AM"
        hour < 12 -> "$hour:00 AM"
        hour == 12 -> "12:00 PM"
        else -> "${hour - 12}:00 PM"
    }

    private fun formatDurationMinutes(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }
}
