package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.TaskCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.AnalyticsSummary
import com.averycorp.prismtask.domain.model.HabitSummaryBucket
import com.averycorp.prismtask.domain.model.StreakSummary
import com.averycorp.prismtask.domain.model.TodaySummary
import com.averycorp.prismtask.domain.model.WeekSummary
import com.averycorp.prismtask.domain.model.WeekTrend
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates the four-tile summary used by the analytics dashboard header
 * (see web PR #715 SummaryTile row, lines 271-301 of AnalyticsScreen.tsx).
 *
 * The data sources are all already Room-backed and Flow-friendly upstream;
 * this aggregator is pure compute over snapshot inputs so it stays fast and
 * trivial to unit-test. Wire it from the ViewModel by combining the source
 * Flows with `.combine` and feeding their values to `compute()`.
 *
 * Trend threshold (10%): keeps STABLE wide enough to not flip on a single
 * extra completion. Same shape as the backend's `determine_trend` helper in
 * `app/services/analytics.py`.
 */
@Singleton
class AnalyticsSummaryAggregator @Inject constructor() {

    fun compute(
        today: LocalDate,
        zone: ZoneId,
        tasksDueToday: List<TaskEntity>,
        completionsLast30Days: List<TaskCompletionEntity>,
        activeHabits: List<HabitEntity>,
        habitCompletionsLast30Days: List<HabitCompletionEntity>
    ): AnalyticsSummary {
        val todayBucket = computeToday(tasksDueToday, completionsLast30Days, today, zone)
        val weekBucket = computeWeek(completionsLast30Days, today, zone)
        val streakBucket = computeStreaks(completionsLast30Days, today, zone)
        val habitBucket = computeHabits(activeHabits, habitCompletionsLast30Days, today, zone)
        return AnalyticsSummary(
            today = todayBucket,
            thisWeek = weekBucket,
            streaks = streakBucket,
            habits = habitBucket
        )
    }

    private fun computeToday(
        tasksDueToday: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        today: LocalDate,
        zone: ZoneId
    ): TodaySummary {
        val completedTodayCount = completions.count { c ->
            Instant.ofEpochMilli(c.completedDate).atZone(zone).toLocalDate() == today
        }
        val remaining = tasksDueToday.count { !it.isCompleted }
        return TodaySummary(completed = completedTodayCount, remaining = remaining)
    }

    private fun computeWeek(
        completions: List<TaskCompletionEntity>,
        today: LocalDate,
        zone: ZoneId
    ): WeekSummary {
        val currentWeekStart = today.minusDays(6)
        val previousWeekEnd = today.minusDays(7)
        val previousWeekStart = today.minusDays(13)

        var current = 0
        var previous = 0
        for (c in completions) {
            val d = Instant.ofEpochMilli(c.completedDate).atZone(zone).toLocalDate()
            when {
                !d.isBefore(currentWeekStart) && !d.isAfter(today) -> current++
                !d.isBefore(previousWeekStart) && !d.isAfter(previousWeekEnd) -> previous++
            }
        }
        return WeekSummary(
            completed = current,
            previousWeekCompleted = previous,
            trend = trendFor(current, previous)
        )
    }

    private fun computeStreaks(
        completions: List<TaskCompletionEntity>,
        today: LocalDate,
        zone: ZoneId
    ): StreakSummary {
        if (completions.isEmpty()) return StreakSummary(currentDays = 0, longestDays = 0)

        val datesWithCompletions = completions
            .map { Instant.ofEpochMilli(it.completedDate).atZone(zone).toLocalDate() }
            .toSet()
            .sorted()

        var current = 0
        var cursor = today
        while (datesWithCompletions.contains(cursor)) {
            current++
            cursor = cursor.minusDays(1)
        }

        var longest = if (datesWithCompletions.isNotEmpty()) 1 else 0
        var run = 1
        for (i in 1 until datesWithCompletions.size) {
            run = if (datesWithCompletions[i] == datesWithCompletions[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }
        return StreakSummary(currentDays = current, longestDays = longest)
    }

    private fun computeHabits(
        activeHabits: List<HabitEntity>,
        completions: List<HabitCompletionEntity>,
        today: LocalDate,
        zone: ZoneId
    ): HabitSummaryBucket {
        val habitCount = activeHabits.size
        if (habitCount == 0) {
            return HabitSummaryBucket(
                completionRate7d = 0.0,
                completionRate30d = 0.0,
                activeHabits = 0
            )
        }

        val activeIds = activeHabits.map { it.id }.toSet()
        val sevenDayStart = today.minusDays(6)
        val thirtyDayStart = today.minusDays(29)

        var sevenDayCount = 0
        var thirtyDayCount = 0
        for (c in completions) {
            if (c.habitId !in activeIds) continue
            val d = Instant.ofEpochMilli(c.completedDate).atZone(zone).toLocalDate()
            if (d.isAfter(today)) continue
            if (!d.isBefore(thirtyDayStart)) {
                thirtyDayCount++
                if (!d.isBefore(sevenDayStart)) sevenDayCount++
            }
        }

        val rate7d = (sevenDayCount.toDouble() / (habitCount * 7.0)).coerceIn(0.0, 1.0)
        val rate30d = (thirtyDayCount.toDouble() / (habitCount * 30.0)).coerceIn(0.0, 1.0)
        return HabitSummaryBucket(
            completionRate7d = rate7d,
            completionRate30d = rate30d,
            activeHabits = habitCount
        )
    }

    companion object {
        const val TREND_THRESHOLD = 0.10

        fun trendFor(current: Int, previous: Int): WeekTrend {
            if (previous == 0) {
                return when {
                    current == 0 -> WeekTrend.STABLE
                    else -> WeekTrend.IMPROVING
                }
            }
            val ratio = (current - previous).toDouble() / previous.toDouble()
            return when {
                ratio > TREND_THRESHOLD -> WeekTrend.IMPROVING
                ratio < -TREND_THRESHOLD -> WeekTrend.DECLINING
                else -> WeekTrend.STABLE
            }
        }
    }
}
