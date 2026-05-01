package com.averycorp.prismtask.ui.screens.today.components

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayScoreBadgeViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `activeDayCount returns zero for empty inputs`() {
        val days = TodayScoreBadgeViewModel.activeDayCount(
            tasks = emptyList(),
            habitCompletions = emptyList(),
            zone = zone
        )
        assertEquals(0, days)
    }

    @Test
    fun `activeDayCount counts only completed tasks with completedAt`() {
        val day1 = LocalDate.of(2026, 4, 28)
        val day2 = LocalDate.of(2026, 4, 29)
        val tasks = listOf(
            taskCompletedOn(day1),
            taskCompletedOn(day2),
            // not completed → ignored
            TaskEntity(title = "x", isCompleted = false),
            // completed but null completedAt → ignored
            TaskEntity(title = "y", isCompleted = true, completedAt = null)
        )

        val days = TodayScoreBadgeViewModel.activeDayCount(
            tasks = tasks,
            habitCompletions = emptyList(),
            zone = zone
        )
        assertEquals(2, days)
    }

    @Test
    fun `activeDayCount unions task and habit days, deduping same-day overlap`() {
        val day1 = LocalDate.of(2026, 4, 28)
        val day2 = LocalDate.of(2026, 4, 29)
        val day3 = LocalDate.of(2026, 4, 30)

        val days = TodayScoreBadgeViewModel.activeDayCount(
            tasks = listOf(taskCompletedOn(day1), taskCompletedOn(day2)),
            habitCompletions = listOf(
                // overlaps with task day → dedup
                habitCompletionOn(day2),
                habitCompletionOn(day3)
            ),
            zone = zone
        )
        assertEquals(3, days)
    }

    @Test
    fun `MIN_ACTIVE_DAYS is exactly three so brand-new users see no badge`() {
        // The badge should be hidden until the user has completed at least
        // one task or habit on three distinct days. This guards against
        // the trivial 100% score that the calculator emits on empty days.
        assertEquals(3, TodayScoreBadgeViewModel.MIN_ACTIVE_DAYS)
    }

    @Test
    fun `WINDOW_DAYS is seven so the calculator has trend context`() {
        // Today's score is the only value consumed, but the calculator
        // benefits from a multi-day window so it can compute the
        // best/worst-day callouts that drive future polish.
        assertEquals(7, TodayScoreBadgeViewModel.WINDOW_DAYS)
    }

    private fun localDateToMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun taskCompletedOn(day: LocalDate): TaskEntity = TaskEntity(
        title = "task",
        dueDate = localDateToMillis(day),
        isCompleted = true,
        completedAt = localDateToMillis(day) + 12 * 3600 * 1000L
    )

    private fun habitCompletionOn(day: LocalDate): HabitCompletionEntity =
        HabitCompletionEntity(
            id = 0L,
            habitId = 1L,
            completedDate = localDateToMillis(day)
        )
}
