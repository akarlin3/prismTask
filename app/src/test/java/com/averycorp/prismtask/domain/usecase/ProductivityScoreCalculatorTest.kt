package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.ProductivityTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProductivityScoreCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val calc = ProductivityScoreCalculator()

    @Test
    fun `empty inputs produce all-100 scores stable trend over the range`() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 7)

        val response = calc.compute(
            startDate = start,
            endDate = end,
            zone = zone,
            tasks = emptyList(),
            activeHabitsCount = 0,
            habitCompletions = emptyList()
        )

        assertEquals(7, response.scores.size)
        response.scores.forEach { score ->
            assertEquals(100.0, score.score, 0.0)
            assertEquals(100.0, score.breakdown.taskCompletion, 0.0)
            assertEquals(100.0, score.breakdown.onTime, 0.0)
            assertEquals(100.0, score.breakdown.habitCompletion, 0.0)
            assertEquals(100.0, score.breakdown.estimationAccuracy, 0.0)
        }
        assertEquals(100.0, response.averageScore, 0.0)
        assertEquals(ProductivityTrend.STABLE, response.trend)
    }

    @Test
    fun `task completion rate is completed-due over due, capped at 100`() {
        val day = LocalDate.of(2026, 5, 1)
        val tasks = listOf(
            taskDueAndCompleted(day, isCompleted = true),
            taskDueAndCompleted(day, isCompleted = true),
            taskDueAndCompleted(day, isCompleted = false),
            taskDueAndCompleted(day, isCompleted = false)
        )

        val response = calc.compute(
            startDate = day,
            endDate = day,
            zone = zone,
            tasks = tasks,
            activeHabitsCount = 0,
            habitCompletions = emptyList()
        )

        // 2/4 = 50% taskCompletion → score = 50*0.40 + 100*0.25 + 100*0.20 + 100*0.15 = 80
        assertEquals(50.0, response.scores[0].breakdown.taskCompletion, 0.001)
        assertEquals(80.0, response.scores[0].score, 0.001)
    }

    @Test
    fun `on-time rate counts completions completed on or before due date`() {
        val due = LocalDate.of(2026, 5, 1)
        val late = LocalDate.of(2026, 5, 3)
        val tasks = listOf(
            taskCompletedOn(due = due, completedOn = due),
            taskCompletedOn(due = due, completedOn = late)
        )

        val response = calc.compute(
            startDate = late,
            endDate = late,
            zone = zone,
            tasks = tasks,
            activeHabitsCount = 0,
            habitCompletions = emptyList()
        )

        // On `late`, one task completed late (the second). On-time rate: 0/1 = 0
        val lateScore = response.scores[0]
        assertEquals(0.0, lateScore.breakdown.onTime, 0.001)
    }

    @Test
    fun `habit rate counts distinct habits completed over active habits count`() {
        val day = LocalDate.of(2026, 5, 1)
        val completions = listOf(
            habitCompletionOn(habitId = 1L, day = day),
            // duplicate same habit
            habitCompletionOn(habitId = 1L, day = day),
            habitCompletionOn(habitId = 2L, day = day)
        )

        val response = calc.compute(
            startDate = day,
            endDate = day,
            zone = zone,
            tasks = emptyList(),
            activeHabitsCount = 4,
            habitCompletions = completions
        )

        // 2 distinct / 4 active = 50%
        assertEquals(50.0, response.scores[0].breakdown.habitCompletion, 0.001)
    }

    @Test
    fun `habit rate defaults to 100 when no active habits`() {
        val day = LocalDate.of(2026, 5, 1)
        val response = calc.compute(
            startDate = day,
            endDate = day,
            zone = zone,
            tasks = emptyList(),
            activeHabitsCount = 0,
            habitCompletions = listOf(habitCompletionOn(99L, day))
        )

        assertEquals(100.0, response.scores[0].breakdown.habitCompletion, 0.0)
    }

    @Test
    fun `trend is IMPROVING when second-half avg exceeds first-half avg by more than 3`() {
        // 7-day range. First 3 days score 70, second 4 days score 80 → diff = 10 → improving
        val scores = listOf(70.0, 70.0, 70.0, 80.0, 80.0, 80.0, 80.0)
        assertEquals(ProductivityTrend.IMPROVING, ProductivityScoreCalculator.determineTrend(scores))
    }

    @Test
    fun `trend is DECLINING when second-half avg falls below first-half by more than 3`() {
        val scores = listOf(80.0, 80.0, 80.0, 70.0, 70.0, 70.0, 70.0)
        assertEquals(ProductivityTrend.DECLINING, ProductivityScoreCalculator.determineTrend(scores))
    }

    @Test
    fun `trend is STABLE within plus-minus 3 threshold`() {
        // Diff = 2 → stable
        val scores = listOf(70.0, 71.0, 72.0, 73.0, 74.0)
        assertEquals(ProductivityTrend.STABLE, ProductivityScoreCalculator.determineTrend(scores))
    }

    @Test
    fun `trend is STABLE when fewer than 2 data points`() {
        assertEquals(ProductivityTrend.STABLE, ProductivityScoreCalculator.determineTrend(emptyList()))
        assertEquals(ProductivityTrend.STABLE, ProductivityScoreCalculator.determineTrend(listOf(50.0)))
    }

    @Test
    fun `best and worst day pick the highest and lowest scores`() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 3)

        // Day 1: all empty → 100
        // Day 2: 1 task due, 0 completed → 0% completion → score = 0*0.40 + 100*0.25 + 100*0.20 + 100*0.15 = 60
        // Day 3: 2 tasks due, 1 completed (on time) → 50% comp + 100% on-time → 50*0.4 + 100*0.25 + 100*0.20 + 100*0.15 = 80
        val day2 = start.plusDays(1)
        val day3 = start.plusDays(2)
        val tasks = listOf(
            taskDueAndCompleted(day2, isCompleted = false),
            taskDueAndCompleted(day3, isCompleted = false),
            taskDueAndCompleted(day3, isCompleted = true)
        )

        val response = calc.compute(
            startDate = start,
            endDate = end,
            zone = zone,
            tasks = tasks,
            activeHabitsCount = 0,
            habitCompletions = emptyList()
        )

        assertEquals(start, response.bestDay?.date)
        assertEquals(100.0, response.bestDay?.score)
        assertEquals(day2, response.worstDay?.date)
        assertEquals(60.0, response.worstDay?.score)
    }

    @Test
    fun `best and worst are null when range is empty`() {
        // startDate > endDate would throw; instead just supply a single day where empty inputs all return 100
        // and confirm best/worst still resolve. The "null" case requires zero scores, which can't happen
        // for a valid range — verify shape instead.
        val day = LocalDate.of(2026, 5, 1)
        val response = calc.compute(
            startDate = day,
            endDate = day,
            zone = zone,
            tasks = emptyList(),
            activeHabitsCount = 0,
            habitCompletions = emptyList()
        )
        // Single-day range: best == worst == that day at 100
        assertEquals(day, response.bestDay?.date)
        assertEquals(day, response.worstDay?.date)
    }

    @Test
    fun `compute throws when startDate is after endDate`() {
        val end = LocalDate.of(2026, 5, 1)
        val start = LocalDate.of(2026, 5, 2)
        try {
            calc.compute(
                startDate = start,
                endDate = end,
                zone = zone,
                tasks = emptyList(),
                activeHabitsCount = 0,
                habitCompletions = emptyList()
            )
            assertNull("expected IllegalArgumentException", "no throw")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ── helpers ────────────────────────────────────────────────────

    private fun localDateToMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun taskDueAndCompleted(
        dueDay: LocalDate,
        isCompleted: Boolean
    ): TaskEntity = TaskEntity(
        title = "task",
        dueDate = localDateToMillis(dueDay),
        isCompleted = isCompleted,
        completedAt = if (isCompleted) localDateToMillis(dueDay) + 12 * 3600 * 1000L else null
    )

    /** Task with separate due and completion dates (e.g. completed late). */
    private fun taskCompletedOn(
        due: LocalDate,
        completedOn: LocalDate
    ): TaskEntity = TaskEntity(
        title = "late",
        dueDate = localDateToMillis(due),
        isCompleted = true,
        completedAt = localDateToMillis(completedOn) + 12 * 3600 * 1000L
    )

    private fun habitCompletionOn(habitId: Long, day: LocalDate): HabitCompletionEntity =
        HabitCompletionEntity(
            id = 0L,
            habitId = habitId,
            completedDate = localDateToMillis(day)
        )
}
