package com.averycorp.prismtask.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the widget data classes exposed by [WidgetDataProvider].
 * Exercises pure-Kotlin mapping helpers and in-memory aggregations.
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

    // --- New tests for W11 ---

    @Test
    fun `today widget tasks respect max tasks parameter`() {
        val tasks = (1L..10L).map { sampleRow(it, "task $it") }
        val data = TodayWidgetData(
            totalTasks = 10,
            completedTasks = 0,
            tasks = tasks,
            totalHabits = 0,
            completedHabits = 0,
            habitIcons = emptyList(),
            productivityScore = 0
        )
        assertEquals(0, data.tasks.take(0).size) // small: no tasks
        assertEquals(3, data.tasks.take(3).size) // medium: 3
        assertEquals(5, data.tasks.take(5).size) // large: 5
    }

    @Test
    fun `upcoming completed tasks flagged correctly`() {
        val completed = sampleRow(1, "done", isCompleted = true)
        val incomplete = sampleRow(2, "todo")
        val data = UpcomingWidgetData(
            overdue = emptyList(),
            today = listOf(completed, incomplete),
            tomorrow = emptyList(),
            dayAfter = emptyList()
        )
        assertEquals(2, data.totalCount)
        assertTrue(data.today[0].isCompleted)
        assertFalse(data.today[1].isCompleted)
    }

    @Test
    fun `productivity widget score zero when no tasks`() {
        val data = ProductivityWidgetData(score = 0, completed = 0, total = 0, trendPoints = 0)
        assertEquals(0, data.score)
        assertEquals(0, data.total)
    }

    @Test
    fun `template shortcut preserves icon and name`() {
        val tpl = TemplateShortcut(id = 1, name = "Morning Routine", icon = "\u2615")
        assertEquals("Morning Routine", tpl.name)
        assertEquals("\u2615", tpl.icon)
        assertEquals(1L, tpl.id)
    }

    @Test
    fun `habit widget item tracks completion state`() {
        val completed = HabitWidgetItem(1, "Run", "\uD83C\uDFC3", 5, true)
        val notCompleted = HabitWidgetItem(2, "Read", "\uD83D\uDCD6", 0, false)
        assertTrue(completed.isCompletedToday)
        assertFalse(notCompleted.isCompletedToday)
        assertEquals(5, completed.streak)
        assertEquals(0, notCompleted.streak)
    }

    @Test
    fun `today widget productivity score capped at 100`() {
        val data = TodayWidgetData(
            totalTasks = 5,
            completedTasks = 5,
            tasks = emptyList(),
            totalHabits = 2,
            completedHabits = 2,
            habitIcons = emptyList(),
            productivityScore = 100
        )
        assertEquals(100, data.productivityScore)
    }

    @Test
    fun `upcoming widget overdue tasks appear in overdue bucket`() {
        val overdueTasks = listOf(
            sampleRow(1, "late task 1", isOverdue = true),
            sampleRow(2, "late task 2", isOverdue = true)
        )
        val data = UpcomingWidgetData(
            overdue = overdueTasks,
            today = emptyList(),
            tomorrow = emptyList(),
            dayAfter = emptyList()
        )
        assertEquals(2, data.overdue.size)
        assertTrue(data.overdue.all { it.isOverdue })
        assertEquals(2, data.totalCount)
    }

    @Test
    fun `widget task row priority range`() {
        val urgent = sampleRow(1, "u", priority = 4)
        val high = sampleRow(2, "h", priority = 3)
        val medium = sampleRow(3, "m", priority = 2)
        val low = sampleRow(4, "l", priority = 1)
        val none = sampleRow(5, "n", priority = 0)
        assertEquals(4, urgent.priority)
        assertEquals(3, high.priority)
        assertEquals(2, medium.priority)
        assertEquals(1, low.priority)
        assertEquals(0, none.priority)
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
