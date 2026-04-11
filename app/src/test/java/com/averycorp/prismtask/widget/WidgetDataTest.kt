package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the widget data classes exposed by [WidgetDataProvider].
 * We can't open a real Room DB from a unit test, but we can exercise
 * the pure-Kotlin mapping helpers and the in-memory aggregations the
 * Glance widgets rely on.
 */
class WidgetDataTest {

    private val startOfToday = 1_700_000_000_000L

    @Test
    fun `upcoming widget total count sums all buckets`() {
        val today = listOf(sampleRow(1, "a"), sampleRow(2, "b"))
        val tomorrow = listOf(sampleRow(3, "c"))
        val dayAfter = listOf(sampleRow(4, "d"), sampleRow(5, "e"), sampleRow(6, "f"))
        val overdue = listOf(sampleRow(7, "o", isOverdue = true))

        val data = UpcomingWidgetData(
            overdue = overdue,
            today = today,
            tomorrow = tomorrow,
            dayAfter = dayAfter
        )

        assertEquals(7, data.totalCount)
    }

    @Test
    fun `empty upcoming widget has zero count`() {
        val data = UpcomingWidgetData(emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(0, data.totalCount)
    }

    @Test
    fun `productivity widget trend positive negative zero`() {
        val up = ProductivityWidgetData(score = 80, completed = 4, total = 5, trendPoints = 10)
        val down = ProductivityWidgetData(score = 40, completed = 2, total = 5, trendPoints = -5)
        val flat = ProductivityWidgetData(score = 50, completed = 2, total = 4, trendPoints = 0)

        assertTrue(up.trendPoints > 0)
        assertTrue(down.trendPoints < 0)
        assertEquals(0, flat.trendPoints)
    }

    @Test
    fun `today widget data carries habit icons`() {
        val data = TodayWidgetData(
            totalTasks = 3,
            completedTasks = 1,
            tasks = emptyList(),
            totalHabits = 2,
            completedHabits = 1,
            habitIcons = listOf("\uD83C\uDFC3", "\uD83D\uDCD6"),
            productivityScore = 60
        )
        assertEquals(2, data.habitIcons.size)
        assertEquals(60, data.productivityScore)
    }

    @Test
    fun `habit widget longest streak is non negative`() {
        val data = HabitWidgetData(habits = emptyList(), longestStreak = 0)
        assertEquals(0, data.longestStreak)
        val data2 = HabitWidgetData(
            habits = listOf(
                HabitWidgetItem(1, "a", "\u2B50", 5, true),
                HabitWidgetItem(2, "b", "\u2B50", 12, false)
            ),
            longestStreak = 12
        )
        assertEquals(12, data2.longestStreak)
    }

    @Test
    fun `widget task row overdue flag`() {
        val row = sampleRow(1, "overdue task", isOverdue = true)
        assertTrue(row.isOverdue)
        assertFalse(row.isCompleted)
    }

    private fun sampleRow(
        id: Long,
        title: String,
        priority: Int = 0,
        dueDate: Long? = null,
        isCompleted: Boolean = false,
        isOverdue: Boolean = false
    ) = WidgetTaskRow(
        id = id,
        title = title,
        priority = priority,
        dueDate = dueDate,
        isCompleted = isCompleted,
        isOverdue = isOverdue
    )
}
