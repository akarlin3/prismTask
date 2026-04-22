package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.TaskEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the weekly task summary aggregation. The pure counting
 * logic lives in [WeeklyTaskSummaryCalculator] so tests can mock a
 * [TaskDao] directly and exercise date-range derivation, overdue-cleared
 * filtering, and still-open reporting without a WorkManager harness.
 *
 * Sibling of [WeeklyHabitSummaryWorkerTest] — follows the same test
 * structure (shared mockk setup in [Before], then one assertion per
 * observable behavior).
 */
class WeeklyTaskSummaryWorkerTest {
    private lateinit var taskDao: TaskDao

    @Before
    fun setUp() {
        taskDao = mockk(relaxed = true)
    }

    private suspend fun generate(): WeeklyTaskSummaryData =
        WeeklyTaskSummaryCalculator.generateWeeklySummary(taskDao)

    @Test
    fun mostRecentMondayMidnight_isMondayAtStartOfDay() {
        val wednesdayNoon = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 22, 12, 30, 45) // Wed 2026-04-22 12:30:45
            set(Calendar.MILLISECOND, 500)
        }.timeInMillis

        val anchor = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(wednesdayNoon)

        val c = Calendar.getInstance().apply { timeInMillis = anchor }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.SECOND))
        assertEquals(0, c.get(Calendar.MILLISECOND))
    }

    @Test
    fun mostRecentMondayMidnight_onSundayRollsBackSixDays() {
        val sundayEvening = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 26, 19, 30, 0) // Sun 2026-04-26 19:30
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val anchor = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(sundayEvening)

        val c = Calendar.getInstance().apply { timeInMillis = anchor }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(20, c.get(Calendar.DAY_OF_MONTH)) // Mon 2026-04-20
    }

    @Test
    fun mostRecentMondayMidnight_onMondayStaysToday() {
        val mondayMidMorning = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 20, 10, 15, 0) // Mon 2026-04-20 10:15
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val anchor = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(mondayMidMorning)

        val c = Calendar.getInstance().apply { timeInMillis = anchor }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(20, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun generateWeeklySummary_reportsCompletedTaskCount() = runBlocking {
        val now = System.currentTimeMillis()
        val weekStart = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(now)
        coEvery { taskDao.getCompletedTasksInRange(weekStart, any()) } returns listOf(
            onTimeCompletion(now - 3_600_000),
            onTimeCompletion(now - 7_200_000),
            onTimeCompletion(now - 86_400_000)
        )
        coEvery { taskDao.getIncompleteTaskCount() } returns 7

        val data = generate()
        assertEquals(3, data.completedCount)
        assertEquals(0, data.overdueClearedCount)
        assertEquals(7, data.stillOpenCount)
    }

    @Test
    fun generateWeeklySummary_overdueClearedCountsOnlyPastDueCompletions() = runBlocking {
        val now = System.currentTimeMillis()
        val weekStart = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(now)
        coEvery { taskDao.getCompletedTasksInRange(weekStart, any()) } returns listOf(
            overdueCompletion(completedAt = now - 3_600_000, dueDate = now - 86_400_000),
            overdueCompletion(completedAt = now - 7_200_000, dueDate = now - 172_800_000),
            onTimeCompletion(now - 10_800_000),
            completionWithoutDueDate(now - 14_400_000)
        )
        coEvery { taskDao.getIncompleteTaskCount() } returns 4

        val data = generate()
        assertEquals(4, data.completedCount)
        assertEquals(2, data.overdueClearedCount)
        assertEquals(4, data.stillOpenCount)
    }

    @Test
    fun generateWeeklySummary_stillOpenReflectsIncompleteTopLevelCount() = runBlocking {
        val now = System.currentTimeMillis()
        val weekStart = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(now)
        coEvery { taskDao.getCompletedTasksInRange(weekStart, any()) } returns emptyList()
        coEvery { taskDao.getIncompleteTaskCount() } returns 12

        val data = generate()
        assertEquals(0, data.completedCount)
        assertEquals(0, data.overdueClearedCount)
        assertEquals(12, data.stillOpenCount)
    }

    @Test
    fun generateWeeklySummary_zeroCompletionsStillReturnsData() = runBlocking {
        val now = System.currentTimeMillis()
        val weekStart = WeeklyTaskSummaryCalculator.mostRecentMondayMidnight(now)
        coEvery { taskDao.getCompletedTasksInRange(weekStart, any()) } returns emptyList()
        coEvery { taskDao.getIncompleteTaskCount() } returns 3

        val data = generate()
        assertEquals(0, data.completedCount)
        assertEquals(0, data.overdueClearedCount)
        assertEquals(3, data.stillOpenCount)
    }

    @Test
    fun formatBody_rendersCountsInExpectedShape() {
        val body = WeeklyTaskSummaryWorker.formatBody(
            WeeklyTaskSummaryData(
                completedCount = 5,
                overdueClearedCount = 2,
                stillOpenCount = 9
            )
        )
        assertEquals(
            "You finished 5 tasks this week, 2 overdue cleared, 9 still open",
            body
        )
    }

    @Test
    fun formatBody_zeroTaskEdgeCaseMatchesSpec() {
        val body = WeeklyTaskSummaryWorker.formatBody(
            WeeklyTaskSummaryData(
                completedCount = 0,
                overdueClearedCount = 0,
                stillOpenCount = 4
            )
        )
        assertEquals(
            "You finished 0 tasks this week, 0 overdue cleared, 4 still open",
            body
        )
    }

    // -- helpers --

    private fun onTimeCompletion(completedAt: Long): TaskEntity = TaskEntity(
        id = nextId(),
        title = "on-time",
        isCompleted = true,
        dueDate = completedAt + 3_600_000,
        completedAt = completedAt,
        updatedAt = completedAt
    )

    private fun overdueCompletion(completedAt: Long, dueDate: Long): TaskEntity = TaskEntity(
        id = nextId(),
        title = "overdue-cleared",
        isCompleted = true,
        dueDate = dueDate,
        completedAt = completedAt,
        updatedAt = completedAt
    )

    private fun completionWithoutDueDate(completedAt: Long): TaskEntity = TaskEntity(
        id = nextId(),
        title = "no-due-date",
        isCompleted = true,
        dueDate = null,
        completedAt = completedAt,
        updatedAt = completedAt
    )

    private var nextIdCounter = 1L
    private fun nextId(): Long = nextIdCounter++
}
