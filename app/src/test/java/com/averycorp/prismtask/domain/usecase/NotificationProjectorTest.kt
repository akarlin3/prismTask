package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotificationProjectorTest {
    private lateinit var taskDao: TaskDao
    private lateinit var habitDao: HabitDao
    private lateinit var projector: NotificationProjector

    @Before
    fun setUp() {
        taskDao = mockk()
        habitDao = mockk()
        projector = NotificationProjector(taskDao, habitDao)
    }

    @Test
    fun `projects task reminder using dueDate minus reminderOffset`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        val due = baseInstant(2026, Calendar.MAY, 1, 14, 0)
        val task = task(id = 1L, title = "Submit Report", dueDate = due, reminderOffset = 30 * 60 * 1000L)
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns listOf(task)
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns emptyList()

        val result = projector.projectNext(limit = 10, nowMillis = now)

        assertEquals(1, result.size)
        val n = result[0]
        assertEquals(due - 30 * 60 * 1000L, n.triggerAtMillis)
        assertEquals("Submit Report is coming up", n.title)
        assertEquals(ProjectedNotification.Source.TASK_REMINDER, n.source)
    }

    @Test
    fun `falls back to default body when task description is null`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        val due = baseInstant(2026, Calendar.MAY, 1, 14, 0)
        val task = task(id = 1L, title = "T", dueDate = due, reminderOffset = 60_000L, description = null)
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns listOf(task)
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns emptyList()

        val result = projector.projectNext(nowMillis = now)

        assertEquals("Ready when you are.", result[0].body)
    }

    @Test
    fun `drops task reminders that are stale by more than 24 hours`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 3, 10, 0)
        val due = baseInstant(2026, Calendar.MAY, 1, 9, 0)
        val task = task(id = 1L, title = "Stale", dueDate = due, reminderOffset = 60_000L)
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns listOf(task)
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns emptyList()

        val result = projector.projectNext(nowMillis = now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `projects habit daily reminder for next occurrence`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 6, 0)
        val nineAm = (9 * 60 * 60 * 1000L)
        val habit = habit(id = 7L, name = "Take Vitamin", reminderTime = nineAm)
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns emptyList()
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns listOf(habit)

        val result = projector.projectNext(nowMillis = now)

        assertEquals(1, result.size)
        val n = result[0]
        assertEquals("Take Vitamin", n.title)
        assertEquals(ProjectedNotification.Source.HABIT_DAILY, n.source)
        assertTrue(n.triggerAtMillis > now)
    }

    @Test
    fun `sorts mixed sources by trigger time and limits to N`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 8, 0)
        val tasks = (1..7L).map { i ->
            task(
                id = i,
                title = "Task $i",
                dueDate = baseInstant(2026, Calendar.MAY, 1, 10 + i.toInt(), 0),
                reminderOffset = 60_000L
            )
        }
        val habits = listOf(
            habit(id = 100L, name = "Morning", reminderTime = 9 * 60 * 60 * 1000L),
            habit(id = 101L, name = "Noon", reminderTime = 12 * 60 * 60 * 1000L),
            habit(id = 102L, name = "Evening", reminderTime = 20 * 60 * 60 * 1000L),
            habit(id = 103L, name = "Night", reminderTime = 22 * 60 * 60 * 1000L)
        )
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns tasks
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns habits

        val result = projector.projectNext(limit = 10, nowMillis = now)

        assertEquals(10, result.size)
        // Strictly non-decreasing
        for (i in 1 until result.size) {
            assertTrue(result[i].triggerAtMillis >= result[i - 1].triggerAtMillis)
        }
    }

    @Test
    fun `excludes tasks without dueDate or reminderOffset`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 8, 0)
        val tasks = listOf(
            task(id = 1L, title = "No due", dueDate = null, reminderOffset = 60_000L),
            task(id = 2L, title = "No offset", dueDate = baseInstant(2026, Calendar.MAY, 1, 14, 0), reminderOffset = null)
        )
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns tasks
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns emptyList()

        val result = projector.projectNext(nowMillis = now)

        assertTrue(result.isEmpty())
    }

    private fun baseInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(year, month, day, hour, minute, 0)
        return cal.timeInMillis
    }

    private fun task(
        id: Long,
        title: String,
        dueDate: Long?,
        reminderOffset: Long?,
        description: String? = "Body $id"
    ): TaskEntity = TaskEntity(
        id = id,
        title = title,
        description = description,
        dueDate = dueDate,
        dueTime = null,
        reminderOffset = reminderOffset
    )

    private fun habit(
        id: Long,
        name: String,
        reminderTime: Long?
    ): HabitEntity = HabitEntity(
        id = id,
        name = name,
        description = null,
        reminderTime = reminderTime
    )
}
