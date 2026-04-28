package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.TaskCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.WeekTrend
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsSummaryAggregatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 5, 1)
    private val aggregator = AnalyticsSummaryAggregator()

    @Test
    fun `today bucket counts completions on today's date and remaining incomplete tasks`() {
        val completions = listOf(
            completionOn(today),
            completionOn(today),
            completionOn(today.minusDays(1)),
        )
        val tasksDueToday = listOf(
            taskDue(isCompleted = false),
            taskDue(isCompleted = false),
            taskDue(isCompleted = true),
        )

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = tasksDueToday,
            completionsLast30Days = completions,
            activeHabits = emptyList(),
            habitCompletionsLast30Days = emptyList(),
        )

        assertEquals(2, summary.today.completed)
        assertEquals(2, summary.today.remaining)
    }

    @Test
    fun `week bucket sums current vs previous 7-day windows`() {
        val completions = buildList {
            // current week (today and 6 prior days): 5 completions
            repeat(5) { add(completionOn(today.minusDays(it.toLong()))) }
            // previous week (days 7-13): 3 completions
            repeat(3) { add(completionOn(today.minusDays((it + 7).toLong()))) }
            // outside both windows
            add(completionOn(today.minusDays(20)))
        }

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = completions,
            activeHabits = emptyList(),
            habitCompletionsLast30Days = emptyList(),
        )

        assertEquals(5, summary.thisWeek.completed)
        assertEquals(3, summary.thisWeek.previousWeekCompleted)
        // (5-3)/3 = 0.67 > 0.10 threshold → IMPROVING
        assertEquals(WeekTrend.IMPROVING, summary.thisWeek.trend)
    }

    @Test
    fun `trend is STABLE when delta is within plus or minus 10 percent`() {
        // 10 vs 11: ratio = 0.10, NOT strictly above the threshold → STABLE
        assertEquals(WeekTrend.STABLE, AnalyticsSummaryAggregator.trendFor(11, 10))
        assertEquals(WeekTrend.STABLE, AnalyticsSummaryAggregator.trendFor(9, 10))
    }

    @Test
    fun `trend handles zero previous week - any current is IMPROVING, none is STABLE`() {
        assertEquals(WeekTrend.STABLE, AnalyticsSummaryAggregator.trendFor(0, 0))
        assertEquals(WeekTrend.IMPROVING, AnalyticsSummaryAggregator.trendFor(1, 0))
    }

    @Test
    fun `streak counts consecutive completed days ending today`() {
        val completions = listOf(
            completionOn(today),
            completionOn(today.minusDays(1)),
            completionOn(today.minusDays(2)),
            // gap on day 3
            completionOn(today.minusDays(4)),
            completionOn(today.minusDays(5)),
        )

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = completions,
            activeHabits = emptyList(),
            habitCompletionsLast30Days = emptyList(),
        )

        assertEquals(3, summary.streaks.currentDays)
        assertEquals(3, summary.streaks.longestDays)
    }

    @Test
    fun `current streak is zero when today has no completion`() {
        val completions = listOf(
            completionOn(today.minusDays(1)),
            completionOn(today.minusDays(2)),
        )

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = completions,
            activeHabits = emptyList(),
            habitCompletionsLast30Days = emptyList(),
        )

        assertEquals(0, summary.streaks.currentDays)
        assertEquals(2, summary.streaks.longestDays)
    }

    @Test
    fun `habit completion rate is completions divided by active habits times window`() {
        val habits = listOf(habit(id = 1L), habit(id = 2L))
        val habitCompletions = buildList {
            // 7-day window: 6 total completions across 2 habits → 6/(2*7) = 0.428
            for (day in 0L..6L) {
                add(habitCompletionOn(habitId = 1L, date = today.minusDays(day)))
            }
            // habit 2 contributes 0 in last 7 days
            // 30-day window adds: habit 2 completes 10 times in days 7-29
            for (day in 7L..16L) {
                add(habitCompletionOn(habitId = 2L, date = today.minusDays(day)))
            }
        }

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = emptyList(),
            activeHabits = habits,
            habitCompletionsLast30Days = habitCompletions,
        )

        // 7-day: 7 completions (all from habit 1) / (2 habits * 7 days) = 0.5
        assertEquals(0.5, summary.habits.completionRate7d, 0.001)
        // 30-day: 7 + 10 = 17 / (2 * 30) = 0.283
        assertEquals(17.0 / 60.0, summary.habits.completionRate30d, 0.001)
        assertEquals(2, summary.habits.activeHabits)
    }

    @Test
    fun `habit rate is zero when no active habits`() {
        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = emptyList(),
            activeHabits = emptyList(),
            habitCompletionsLast30Days = listOf(habitCompletionOn(habitId = 99L, date = today)),
        )

        assertEquals(0.0, summary.habits.completionRate7d, 0.0)
        assertEquals(0.0, summary.habits.completionRate30d, 0.0)
        assertEquals(0, summary.habits.activeHabits)
    }

    @Test
    fun `habit rate ignores completions for archived (non-active) habits`() {
        val habits = listOf(habit(id = 1L))
        val completions = listOf(
            habitCompletionOn(habitId = 1L, date = today),
            habitCompletionOn(habitId = 999L, date = today), // archived habit
        )

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = emptyList(),
            activeHabits = habits,
            habitCompletionsLast30Days = completions,
        )

        // 1 completion for active habit / (1 * 7) = 0.143
        assertEquals(1.0 / 7.0, summary.habits.completionRate7d, 0.001)
    }

    @Test
    fun `habit rate caps at 1 point 0 when completions exceed window`() {
        val habits = listOf(habit(id = 1L))
        // 14 completions for 1 habit in 7 days (multiple per day) → would be 2.0, must cap to 1.0
        val completions = buildList {
            for (day in 0L..6L) {
                add(habitCompletionOn(habitId = 1L, date = today.minusDays(day)))
                add(habitCompletionOn(habitId = 1L, date = today.minusDays(day)))
            }
        }

        val summary = aggregator.compute(
            today = today,
            zone = zone,
            tasksDueToday = emptyList(),
            completionsLast30Days = emptyList(),
            activeHabits = habits,
            habitCompletionsLast30Days = completions,
        )

        assertEquals(1.0, summary.habits.completionRate7d, 0.0)
    }

    // ── helpers ────────────────────────────────────────────────────

    private fun localDateToMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun completionOn(date: LocalDate): TaskCompletionEntity = TaskCompletionEntity(
        id = 0L,
        taskId = 1L,
        projectId = null,
        completedDate = localDateToMillis(date),
        completedAtTime = localDateToMillis(date) + 12 * 3600 * 1000L,
        priority = 0,
        wasOverdue = false,
        daysToComplete = 0,
        tags = null,
    )

    private fun taskDue(isCompleted: Boolean): TaskEntity = TaskEntity(
        title = "due",
        isCompleted = isCompleted,
    )

    private fun habit(id: Long): HabitEntity = HabitEntity(
        id = id,
        name = "habit-$id",
    )

    private fun habitCompletionOn(habitId: Long, date: LocalDate): HabitCompletionEntity =
        HabitCompletionEntity(
            id = 0L,
            habitId = habitId,
            completedDate = localDateToMillis(date),
        )
}
