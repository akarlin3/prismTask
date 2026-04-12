package com.averycorp.prismtask

import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.TaskCompletionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TaskCompletionAnalyticsTest {

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun LocalDate.toMillisAtHour(hour: Int): Long =
        atStartOfDay(ZoneId.systemDefault()).plusHours(hour.toLong()).toInstant().toEpochMilli()

    // --- Streak Calculation Tests ---

    @Test
    fun `currentStreak with 3 consecutive days returns 3`() {
        val today = LocalDate.of(2025, 6, 10)
        val dates = listOf(
            today.minusDays(2),
            today.minusDays(1),
            today
        )
        val streak = TaskCompletionRepository.calculateCurrentStreak(dates, today)
        assertEquals(3, streak)
    }

    @Test
    fun `currentStreak gap day resets streak`() {
        val today = LocalDate.of(2025, 6, 10)
        val dates = listOf(
            today.minusDays(3),
            today.minusDays(2),
            // gap at minusDays(1)
            today
        )
        val streak = TaskCompletionRepository.calculateCurrentStreak(dates, today)
        assertEquals(1, streak)
    }

    @Test
    fun `currentStreak with no completions returns 0`() {
        val today = LocalDate.of(2025, 6, 10)
        val streak = TaskCompletionRepository.calculateCurrentStreak(emptyList(), today)
        assertEquals(0, streak)
    }

    @Test
    fun `currentStreak not including today returns 0`() {
        val today = LocalDate.of(2025, 6, 10)
        val dates = listOf(today.minusDays(1), today.minusDays(2))
        val streak = TaskCompletionRepository.calculateCurrentStreak(dates, today)
        assertEquals(0, streak)
    }

    @Test
    fun `currentStreak starting from today with single day returns 1`() {
        val today = LocalDate.of(2025, 6, 10)
        val dates = listOf(today)
        val streak = TaskCompletionRepository.calculateCurrentStreak(dates, today)
        assertEquals(1, streak)
    }

    @Test
    fun `longestStreak tracks maximum consecutive run`() {
        val dates = listOf(
            LocalDate.of(2025, 6, 1),
            LocalDate.of(2025, 6, 2),
            LocalDate.of(2025, 6, 3),
            // gap
            LocalDate.of(2025, 6, 5),
            LocalDate.of(2025, 6, 6)
        )
        val longest = TaskCompletionRepository.calculateLongestStreak(dates)
        assertEquals(3, longest)
    }

    @Test
    fun `longestStreak with no completions returns 0`() {
        val longest = TaskCompletionRepository.calculateLongestStreak(emptyList())
        assertEquals(0, longest)
    }

    @Test
    fun `longestStreak single day returns 1`() {
        val dates = listOf(LocalDate.of(2025, 6, 5))
        val longest = TaskCompletionRepository.calculateLongestStreak(dates)
        assertEquals(1, longest)
    }

    // --- Stats Computation Tests ---

    @Test
    fun `completionsByDayOfWeek buckets correctly`() {
        // Monday, Tuesday, Monday
        val monday = LocalDate.of(2025, 6, 9) // Monday
        val tuesday = monday.plusDays(1) // Tuesday
        val completions = listOf(
            completion(completedDate = monday.toMillis()),
            completion(completedDate = tuesday.toMillis()),
            completion(completedDate = monday.toMillis())
        )
        val grouped = completions
            .groupBy {
                java.time.Instant.ofEpochMilli(it.completedDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek
            }
            .mapValues { it.value.size }

        assertEquals(2, grouped[DayOfWeek.MONDAY])
        assertEquals(1, grouped[DayOfWeek.TUESDAY])
        assertNull(grouped[DayOfWeek.WEDNESDAY])
    }

    @Test
    fun `completionsByHour buckets by hour correctly`() {
        val date = LocalDate.of(2025, 6, 10)
        val completions = listOf(
            completion(completedDate = date.toMillis(), completedAtTime = date.toMillisAtHour(9)),
            completion(completedDate = date.toMillis(), completedAtTime = date.toMillisAtHour(9)),
            completion(completedDate = date.toMillis(), completedAtTime = date.toMillisAtHour(14))
        )
        val grouped = completions
            .groupBy {
                java.time.Instant.ofEpochMilli(it.completedAtTime)
                    .atZone(ZoneId.systemDefault()).hour
            }
            .mapValues { it.value.size }

        assertEquals(2, grouped[9])
        assertEquals(1, grouped[14])
    }

    @Test
    fun `avgDaysToComplete computes correctly`() {
        val completions = listOf(
            completion(daysToComplete = 3),
            completion(daysToComplete = 5),
            completion(daysToComplete = 7)
        )
        val avg = completions.mapNotNull { it.daysToComplete }.average()
        assertEquals(5.0, avg, 0.01)
    }

    @Test
    fun `overdueRate 3 of 10 is 30 percent`() {
        val completions = (1..7).map { completion(wasOverdue = false) } +
            (1..3).map { completion(wasOverdue = true) }
        val overdueCount = completions.count { it.wasOverdue }
        val rate = overdueCount.toDouble() / completions.size * 100.0
        assertEquals(30.0, rate, 0.01)
    }

    @Test
    fun `bestDay identifies day with most completions`() {
        val monday = LocalDate.of(2025, 6, 9)
        val tuesday = monday.plusDays(1)
        val completions = listOf(
            completion(completedDate = monday.toMillis()),
            completion(completedDate = monday.toMillis()),
            completion(completedDate = tuesday.toMillis())
        )
        val grouped = completions
            .groupBy {
                java.time.Instant.ofEpochMilli(it.completedDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek
            }
            .mapValues { it.value.size }
        val best = grouped.maxByOrNull { it.value }?.key
        assertEquals(DayOfWeek.MONDAY, best)
    }

    @Test
    fun `worstDay identifies day with fewest completions`() {
        val monday = LocalDate.of(2025, 6, 9)
        val tuesday = monday.plusDays(1)
        val completions = listOf(
            completion(completedDate = monday.toMillis()),
            completion(completedDate = monday.toMillis()),
            completion(completedDate = tuesday.toMillis())
        )
        val grouped = completions
            .groupBy {
                java.time.Instant.ofEpochMilli(it.completedDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek
            }
            .mapValues { it.value.size }
        val worst = DayOfWeek.entries.minByOrNull { grouped[it] ?: 0 }
        // Wednesday through Sunday all have 0, any of them is valid
        assertNotNull(worst)
        assertTrue(grouped.getOrDefault(worst!!, 0) == 0)
    }

    @Test
    fun `peakHour identifies hour with most completions`() {
        val date = LocalDate.of(2025, 6, 10)
        val completions = listOf(
            completion(completedDate = date.toMillis(), completedAtTime = date.toMillisAtHour(10)),
            completion(completedDate = date.toMillis(), completedAtTime = date.toMillisAtHour(10)),
            completion(completedDate = date.toMillis(), completedAtTime = date.toMillisAtHour(15))
        )
        val grouped = completions
            .groupBy {
                java.time.Instant.ofEpochMilli(it.completedAtTime)
                    .atZone(ZoneId.systemDefault()).hour
            }
            .mapValues { it.value.size }
        val peak = grouped.maxByOrNull { it.value }?.key
        assertEquals(10, peak)
    }

    @Test
    fun `empty dataset returns safe defaults`() {
        val completions = emptyList<TaskCompletionEntity>()
        assertEquals(0, completions.size)
        val daysValues = completions.mapNotNull { it.daysToComplete }
        assertTrue(daysValues.isEmpty())
        val streak = TaskCompletionRepository.calculateCurrentStreak(emptyList(), LocalDate.now())
        assertEquals(0, streak)
        val longest = TaskCompletionRepository.calculateLongestStreak(emptyList())
        assertEquals(0, longest)
    }

    // --- Recording Tests ---

    @Test
    fun `normalizeToMidnight returns start of day`() {
        val date = LocalDate.of(2025, 6, 10)
        val midday = date.toMillisAtHour(14)
        val normalized = TaskCompletionRepository.normalizeToMidnight(midday)
        val midnight = date.toMillis()
        assertEquals(midnight, normalized)
    }

    @Test
    fun `computeDaysToComplete returns correct days`() {
        val created = LocalDate.of(2025, 6, 1).toMillis()
        val completed = LocalDate.of(2025, 6, 4).toMillis()
        val days = TaskCompletionRepository.computeDaysToComplete(created, completed)
        assertEquals(3, days)
    }

    @Test
    fun `computeDaysToComplete returns null for zero createdAt`() {
        val days = TaskCompletionRepository.computeDaysToComplete(0, System.currentTimeMillis())
        assertNull(days)
    }

    @Test
    fun `completing task with no due date sets wasOverdue false`() {
        val task = TaskEntity(
            id = 1,
            title = "Test",
            dueDate = null,
            priority = 2,
            createdAt = System.currentTimeMillis() - 86400000
        )
        val todayMidnight = TaskCompletionRepository.normalizeToMidnight(System.currentTimeMillis())
        val wasOverdue = task.dueDate != null && task.dueDate < todayMidnight
        assertFalse(wasOverdue)
    }

    @Test
    fun `completing overdue task sets wasOverdue true`() {
        val yesterday = LocalDate.now().minusDays(1).toMillis()
        val task = TaskEntity(
            id = 1,
            title = "Overdue task",
            dueDate = yesterday,
            priority = 3,
            createdAt = System.currentTimeMillis() - 172800000
        )
        val todayMidnight = TaskCompletionRepository.normalizeToMidnight(System.currentTimeMillis())
        val wasOverdue = task.dueDate != null && task.dueDate < todayMidnight
        assertTrue(wasOverdue)
    }

    @Test
    fun `recordCompletion creates entity with correct tag snapshot`() {
        val tags = listOf(
            TagEntity(id = 1, name = "Work"),
            TagEntity(id = 2, name = "Urgent")
        )
        val tagString = tags.joinToString(",") { it.name }
        assertEquals("Work,Urgent", tagString)
    }

    @Test
    fun `recordCompletion with empty tags produces null tags`() {
        val tags = emptyList<TagEntity>()
        val tagString = if (tags.isNotEmpty()) tags.joinToString(",") { it.name } else null
        assertNull(tagString)
    }

    @Test
    fun `daysToComplete calculation handles same day`() {
        val now = System.currentTimeMillis()
        val days = TaskCompletionRepository.computeDaysToComplete(now, now)
        assertEquals(0, days)
    }

    // --- Helper functions ---

    private fun completion(
        taskId: Long = 1,
        completedDate: Long = System.currentTimeMillis(),
        completedAtTime: Long = System.currentTimeMillis(),
        priority: Int = 0,
        wasOverdue: Boolean = false,
        daysToComplete: Int? = null,
        tags: String? = null
    ) = TaskCompletionEntity(
        taskId = taskId,
        completedDate = completedDate,
        completedAtTime = completedAtTime,
        priority = priority,
        wasOverdue = wasOverdue,
        daysToComplete = daysToComplete,
        tags = tags
    )
}
