package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.preferences.ProductiveStreakSnapshot
import com.averycorp.prismtask.data.repository.TaskCompletionStats
import com.averycorp.prismtask.domain.model.AnalyticsSummary
import com.averycorp.prismtask.domain.model.BestWorstDay
import com.averycorp.prismtask.domain.model.DailyScore
import com.averycorp.prismtask.domain.model.HabitSummaryBucket
import com.averycorp.prismtask.domain.model.ProductivityRange
import com.averycorp.prismtask.domain.model.ProductivityScoreResponse
import com.averycorp.prismtask.domain.model.ProductivityTrend
import com.averycorp.prismtask.domain.model.ScoreBreakdown
import com.averycorp.prismtask.domain.model.StreakSummary
import com.averycorp.prismtask.domain.model.TimeTrackingResponse
import com.averycorp.prismtask.domain.model.TodaySummary
import com.averycorp.prismtask.domain.model.WeekSummary
import com.averycorp.prismtask.domain.model.WeekTrend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsMarkdownExporterTest {

    private val exporter = AnalyticsMarkdownExporter()

    @Test
    fun `header carries the generated date and range`() {
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 30,
            productivity = null,
            timeTracking = null,
            stats = null,
            summary = null,
            streak = null
        )

        assertTrue(md.contains("# PrismTask Analytics Report"))
        assertTrue(md.contains("Generated: 2026-05-01"))
        assertTrue(md.contains("Range: last 30 days"))
    }

    @Test
    fun `productivity section renders average trend and best worst day`() {
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 30,
            productivity = sampleProductivity(),
            timeTracking = null,
            stats = null,
            summary = null,
            streak = null
        )

        assertTrue(md.contains("## Productivity Score"))
        assertTrue(md.contains("Average: **78/100**"))
        assertTrue(md.contains("Trend: Improving"))
        assertTrue(md.contains("Best Day: 2026-04-30 — 92/100"))
        assertTrue(md.contains("Worst Day: 2026-04-25 — 55/100"))
    }

    @Test
    fun `streak section renders only when there is history`() {
        val empty = ProductiveStreakSnapshot(currentDays = 0, longestDays = 0, lastProductiveDate = null)
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 7,
            productivity = null,
            timeTracking = null,
            stats = null,
            summary = null,
            streak = empty
        )

        assertFalse(md.contains("## Productive-Day Streak"))
    }

    @Test
    fun `streak section includes current and longest run when present`() {
        val streak = ProductiveStreakSnapshot(
            currentDays = 4,
            longestDays = 12,
            lastProductiveDate = LocalDate.of(2026, 4, 30)
        )
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 30,
            productivity = null,
            timeTracking = null,
            stats = null,
            summary = null,
            streak = streak
        )

        assertTrue(md.contains("## Productive-Day Streak"))
        assertTrue(md.contains("Current run: 4 day(s)"))
        assertTrue(md.contains("Longest run: 12 day(s)"))
        assertTrue(md.contains("Last productive day: 2026-04-30"))
    }

    @Test
    fun `task stats render completed and on-time rate when available`() {
        val stats = TaskCompletionStats(
            totalCompleted = 42,
            avgDaysToComplete = 1.5,
            overdueRate = 25.0,
            currentStreak = 3,
            longestStreak = 9,
            completionRate7Day = 4.2,
            completionRate30Day = 3.8
        )
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 30,
            productivity = null,
            timeTracking = null,
            stats = stats,
            summary = null,
            streak = null
        )

        assertTrue(md.contains("## Task Stats"))
        assertTrue(md.contains("Total completed: 42"))
        assertTrue(md.contains("On-time rate: 75%"))
        assertTrue(md.contains("Current task streak: 3 day(s)"))
        assertTrue(md.contains("Longest task streak: 9 day(s)"))
    }

    @Test
    fun `time tracking only renders when totalMinutes is positive`() {
        val zero = TimeTrackingResponse(
            buckets = emptyList(),
            totalMinutes = 0,
            averageMinutesPerActiveDay = 0,
            activeDayCount = 0,
            range = ProductivityRange.SEVEN_DAYS
        )
        val mdEmpty = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 7,
            productivity = null,
            timeTracking = zero,
            stats = null,
            summary = null,
            streak = null
        )
        assertFalse(mdEmpty.contains("## Time Tracked"))

        val populated = TimeTrackingResponse(
            buckets = emptyList(),
            totalMinutes = 145, // 2h 25m
            averageMinutesPerActiveDay = 30,
            activeDayCount = 5,
            range = ProductivityRange.SEVEN_DAYS
        )
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 7,
            productivity = null,
            timeTracking = populated,
            stats = null,
            summary = null,
            streak = null
        )
        assertTrue(md.contains("## Time Tracked"))
        assertTrue(md.contains("Total: 2h 25m"))
        assertTrue(md.contains("Average per active day: 30m"))
        assertTrue(md.contains("Active days: 5"))
    }

    @Test
    fun `summary renders today and habits sections`() {
        val summary = AnalyticsSummary(
            today = TodaySummary(completed = 5, remaining = 2),
            thisWeek = WeekSummary(completed = 18, previousWeekCompleted = 14, trend = WeekTrend.IMPROVING),
            streaks = StreakSummary(currentDays = 3, longestDays = 9),
            habits = HabitSummaryBucket(
                completionRate7d = 0.6,
                completionRate30d = 0.55,
                activeHabits = 4
            )
        )
        val md = exporter.build(
            generatedOn = LocalDate.of(2026, 5, 1),
            rangeDays = 30,
            productivity = null,
            timeTracking = null,
            stats = null,
            summary = summary,
            streak = null
        )

        assertTrue(md.contains("## Today"))
        assertTrue(md.contains("Completed: 5"))
        assertTrue(md.contains("Remaining: 2"))
        assertTrue(md.contains("## Habits (7d)"))
        assertTrue(md.contains("Completion rate: 60%"))
        assertTrue(md.contains("Active habits: 4"))
    }

    private fun sampleProductivity(): ProductivityScoreResponse =
        ProductivityScoreResponse(
            scores = listOf(
                DailyScore(LocalDate.of(2026, 4, 25), 55.0, sampleBreakdown(55.0)),
                DailyScore(LocalDate.of(2026, 4, 30), 92.0, sampleBreakdown(92.0))
            ),
            averageScore = 78.0,
            trend = ProductivityTrend.IMPROVING,
            bestDay = BestWorstDay(LocalDate.of(2026, 4, 30), 92.0),
            worstDay = BestWorstDay(LocalDate.of(2026, 4, 25), 55.0)
        )

    private fun sampleBreakdown(score: Double) =
        ScoreBreakdown(
            taskCompletion = score,
            onTime = score,
            habitCompletion = score,
            estimationAccuracy = score
        )
}
