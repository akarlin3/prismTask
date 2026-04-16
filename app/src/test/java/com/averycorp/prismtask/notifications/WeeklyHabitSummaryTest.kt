package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.HabitRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

/**
 * Unit tests for [WeeklyHabitSummary.generateWeeklySummary]. The rendering
 * side that talks to [android.app.NotificationManager] is not tested here;
 * it needs instrumentation to do anything meaningful. The aggregation logic
 * is pure enough to verify with mocked DAOs.
 */
class WeeklyHabitSummaryTest {
    private lateinit var habitDao: HabitDao
    private lateinit var completionDao: HabitCompletionDao
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var summary: WeeklyHabitSummary

    @Before
    fun setUp() {
        habitDao = mockk(relaxed = true)
        completionDao = mockk(relaxed = true)
        taskBehaviorPreferences = mockk(relaxed = true)
        every { taskBehaviorPreferences.getFirstDayOfWeek() } returns flowOf(DayOfWeek.MONDAY)
        summary = WeeklyHabitSummary(habitDao, completionDao, taskBehaviorPreferences)
    }

    @Test
    fun generateWeeklySummary_emptyHabitsYieldsZeroedData() = runBlocking {
        coEvery { habitDao.getActiveHabitsOnce() } returns emptyList()

        val data = summary.generateWeeklySummary()

        assertEquals(0, data.totalHabits)
        assertEquals(0, data.totalCompletions)
        assertEquals(0f, data.completionRate, 0.0001f)
        assertNull(data.bestHabit)
        assertNull(data.worstHabit)
    }

    @Test
    fun generateWeeklySummary_totalCompletionsCountsInWeekOnly() = runBlocking {
        val today = HabitRepository.normalizeToMidnight(System.currentTimeMillis())
        val weekStart = HabitRepository.getWeekStart(today)
        val weekEnd = HabitRepository.getWeekEnd(today)

        val habit = HabitEntity(id = 1L, name = "Water", targetFrequency = 1)
        coEvery { habitDao.getActiveHabitsOnce() } returns listOf(habit)
        coEvery { completionDao.getCompletionsForHabitOnce(1L) } returns listOf(
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekEnd),
            // Outside the week — must be excluded.
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart - 1L),
            HabitCompletionEntity(habitId = 1L, completedDate = weekEnd + 1L)
        )

        val data = summary.generateWeeklySummary()
        assertEquals(1, data.totalHabits)
        assertEquals(2, data.totalCompletions)
    }

    @Test
    fun generateWeeklySummary_bestAndWorstHabitsReflectCompletionCounts() = runBlocking {
        val today = HabitRepository.normalizeToMidnight(System.currentTimeMillis())
        val weekStart = HabitRepository.getWeekStart(today)

        val a = HabitEntity(id = 1L, name = "Water", targetFrequency = 1)
        val b = HabitEntity(id = 2L, name = "Stretch", targetFrequency = 1)
        coEvery { habitDao.getActiveHabitsOnce() } returns listOf(a, b)
        coEvery { completionDao.getCompletionsForHabitOnce(1L) } returns listOf(
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart)
        )
        coEvery { completionDao.getCompletionsForHabitOnce(2L) } returns emptyList()

        val data = summary.generateWeeklySummary()
        assertEquals("Water", data.bestHabit)
        assertEquals("Stretch", data.worstHabit)
        assertEquals(3, data.totalCompletions)
    }

    @Test
    fun generateWeeklySummary_completionRateIsCompletionsOverWeeklyTarget() = runBlocking {
        val today = HabitRepository.normalizeToMidnight(System.currentTimeMillis())
        val weekStart = HabitRepository.getWeekStart(today)

        val habit = HabitEntity(id = 1L, name = "Daily", targetFrequency = 1)
        coEvery { habitDao.getActiveHabitsOnce() } returns listOf(habit)
        coEvery { completionDao.getCompletionsForHabitOnce(1L) } returns listOf(
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart),
            HabitCompletionEntity(habitId = 1L, completedDate = weekStart)
        )

        val data = summary.generateWeeklySummary()
        // Target = targetFrequency (1) * 7 = 7; completions = 7 → 100%.
        assertEquals(1f, data.completionRate, 0.0001f)
    }

    @Test
    fun generateWeeklySummary_totalHabitsMatchesActiveCount() = runBlocking {
        coEvery { habitDao.getActiveHabitsOnce() } returns listOf(
            HabitEntity(id = 1L, name = "A"),
            HabitEntity(id = 2L, name = "B"),
            HabitEntity(id = 3L, name = "C")
        )
        coEvery { completionDao.getCompletionsForHabitOnce(any()) } returns emptyList()

        val data = summary.generateWeeklySummary()
        assertEquals(3, data.totalHabits)
        // With zero completions in the week, rate collapses to 0.
        assertTrue(data.completionRate == 0f)
    }
}
