package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.AdvancedTuningPreferences
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.data.preferences.OverloadCheckSchedule
import com.averycorp.prismtask.data.preferences.ReengagementConfig
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.WeeklySummarySchedule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [NotificationProjector]. All worker toggles default OFF
 * via [silenceWorkers] so each test only exercises the source it cares
 * about; tests that need a worker enabled override that toggle locally.
 */
class NotificationProjectorTest {
    private lateinit var taskDao: TaskDao
    private lateinit var habitDao: HabitDao
    private lateinit var habitCompletionDao: HabitCompletionDao
    private lateinit var medicationDao: MedicationDao
    private lateinit var medicationDoseDao: MedicationDoseDao
    private lateinit var notificationPreferences: NotificationPreferences
    private lateinit var advancedTuningPreferences: AdvancedTuningPreferences
    private lateinit var medicationPreferences: MedicationPreferences
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var projector: NotificationProjector

    @Before
    fun setUp() = runBlocking {
        taskDao = mockk()
        habitDao = mockk()
        habitCompletionDao = mockk()
        medicationDao = mockk()
        medicationDoseDao = mockk()
        notificationPreferences = mockk()
        advancedTuningPreferences = mockk()
        medicationPreferences = mockk()
        taskBehaviorPreferences = mockk()
        projector = NotificationProjector(
            taskDao,
            habitDao,
            habitCompletionDao,
            medicationDao,
            medicationDoseDao,
            notificationPreferences,
            advancedTuningPreferences,
            medicationPreferences,
            taskBehaviorPreferences
        )
        defaultEmptyDataSources()
        silenceWorkers()
    }

    private fun defaultEmptyDataSources() = runBlocking {
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns emptyList()
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns emptyList()
        coEvery { habitDao.getHabitsWithIntervalReminder() } returns emptyList()
        coEvery { medicationDao.getActiveOnce() } returns emptyList()
        coEvery { medicationPreferences.getScheduleModeOnce() } returns MedicationScheduleMode.INTERVAL
        coEvery { medicationPreferences.getSpecificTimesOnce() } returns emptySet()
        every { taskBehaviorPreferences.getDayStartHour() } returns flowOf(0)
    }

    /**
     * All worker toggles OFF + default schedules. Tests that exercise a
     * specific worker re-stub its enabled flag to `true`.
     */
    private fun silenceWorkers() {
        every { notificationPreferences.dailyBriefingEnabled } returns flowOf(false)
        every { notificationPreferences.eveningSummaryEnabled } returns flowOf(false)
        every { notificationPreferences.reengagementEnabled } returns flowOf(false)
        every { notificationPreferences.weeklySummaryEnabled } returns flowOf(false)
        every { notificationPreferences.weeklyTaskSummaryEnabled } returns flowOf(false)
        every { notificationPreferences.overloadAlertsEnabled } returns flowOf(false)
        every { notificationPreferences.weeklyReviewAutoGenerateEnabled } returns flowOf(false)
        every { notificationPreferences.weeklyReviewNotificationEnabled } returns flowOf(false)
        every { notificationPreferences.briefingMorningHour } returns flowOf(8)
        every { advancedTuningPreferences.getWeeklySummarySchedule() } returns flowOf(
            WeeklySummarySchedule(
                dayOfWeek = 7,
                taskSummaryHour = 19,
                taskSummaryMinute = 30,
                habitSummaryHour = 19,
                habitSummaryMinute = 0,
                reviewHour = 20,
                reviewMinute = 0,
                eveningSummaryHour = 20
            )
        )
        every { advancedTuningPreferences.getOverloadCheckSchedule() } returns flowOf(
            OverloadCheckSchedule(hourOfDay = 16, minute = 0)
        )
        every { advancedTuningPreferences.getReengagementConfig() } returns flowOf(
            ReengagementConfig(absenceDays = 2, maxNudges = 1)
        )
    }

    @Test
    fun `projects task reminder using dueDate minus reminderOffset`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        val due = baseInstant(2026, Calendar.MAY, 1, 14, 0)
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns listOf(
            task(id = 1L, title = "Submit Report", dueDate = due, reminderOffset = 30 * 60 * 1000L)
        )

        val result = projector.projectAll(nowMillis = now)

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
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns listOf(
            task(id = 1L, title = "T", dueDate = due, reminderOffset = 60_000L, description = null)
        )

        val result = projector.projectAll(nowMillis = now)

        assertEquals("Ready when you are.", result[0].body)
    }

    @Test
    fun `drops task reminders that are stale by more than 24 hours`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 3, 10, 0)
        val due = baseInstant(2026, Calendar.MAY, 1, 9, 0)
        coEvery { taskDao.getIncompleteTasksWithReminders() } returns listOf(
            task(id = 1L, title = "Stale", dueDate = due, reminderOffset = 60_000L)
        )

        val result = projector.projectAll(nowMillis = now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `projects habit daily reminder for every occurrence within horizon`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 6, 0)
        val nineAm = (9 * 60 * 60 * 1000L)
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns listOf(
            habit(id = 7L, name = "Take Vitamin", reminderTime = nineAm)
        )

        val result = projector.projectAll(nowMillis = now)

        assertEquals(7, result.size)
        result.forEach {
            assertEquals(ProjectedNotification.Source.HABIT_DAILY, it.source)
            assertEquals("Take Vitamin", it.title)
        }
    }

    @Test
    fun `projects habit interval reminder when last completion exists and cap not reached`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        val lastCompletion = baseInstant(2026, Calendar.MAY, 1, 8, 0)
        val intervalMs = 4 * 60 * 60 * 1000L
        coEvery { habitDao.getHabitsWithIntervalReminder() } returns listOf(
            habit(id = 9L, name = "Hydrate", reminderIntervalMillis = intervalMs, reminderTimesPerDay = 4)
        )
        coEvery { habitCompletionDao.getLastCompletionOnce(9L) } returns completion(9L, lastCompletion)
        coEvery { habitCompletionDao.getCompletionCountForDateLocalOnce(9L, any()) } returns 1

        val result = projector.projectAll(nowMillis = now)

        assertEquals(1, result.size)
        val n = result[0]
        assertEquals(lastCompletion + intervalMs, n.triggerAtMillis)
        assertEquals("Hydrate (dose 2 of 4)", n.title)
        assertEquals(ProjectedNotification.Source.HABIT_INTERVAL, n.source)
    }

    @Test
    fun `skips habit interval when daily cap already met`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        coEvery { habitDao.getHabitsWithIntervalReminder() } returns listOf(
            habit(id = 9L, name = "Hydrate", reminderIntervalMillis = 60_000L, reminderTimesPerDay = 2)
        )
        coEvery { habitCompletionDao.getCompletionCountForDateLocalOnce(9L, any()) } returns 2

        val result = projector.projectAll(nowMillis = now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips habit interval when global mode is SPECIFIC_TIMES`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        coEvery { medicationPreferences.getScheduleModeOnce() } returns MedicationScheduleMode.SPECIFIC_TIMES
        coEvery { habitDao.getHabitsWithIntervalReminder() } returns listOf(
            habit(id = 9L, name = "Hydrate", reminderIntervalMillis = 60_000L)
        )

        val result = projector.projectAll(nowMillis = now)

        assertTrue(result.none { it.source == ProjectedNotification.Source.HABIT_INTERVAL })
    }

    @Test
    fun `projects medication TIMES_OF_DAY at canonical clock times`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 6, 0)
        coEvery { medicationDao.getActiveOnce() } returns listOf(
            medication(id = 11L, name = "Vitamin D", scheduleMode = "TIMES_OF_DAY", timesOfDay = "morning,evening")
        )

        val result = projector.projectAll(nowMillis = now)

        assertTrue(result.isNotEmpty())
        result.forEach {
            assertEquals(ProjectedNotification.Source.MEDICATION, it.source)
        }
        // Two slots × 7 days = 14 occurrences within horizon
        assertEquals(14, result.size)
    }

    @Test
    fun `projects medication INTERVAL using last dose plus interval`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        val lastDose = baseInstant(2026, Calendar.MAY, 1, 8, 0)
        val interval = 6 * 60 * 60 * 1000L
        coEvery { medicationDao.getActiveOnce() } returns listOf(
            medication(id = 12L, name = "Insulin", scheduleMode = "INTERVAL", intervalMillis = interval)
        )
        coEvery { medicationDoseDao.getLatestForMedOnce(12L) } returns dose(12L, lastDose)

        val result = projector.projectAll(nowMillis = now)

        assertEquals(1, result.size)
        assertEquals(lastDose + interval, result[0].triggerAtMillis)
    }

    @Test
    fun `briefing worker projects daily occurrences when enabled`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 6, 0)
        every { notificationPreferences.dailyBriefingEnabled } returns flowOf(true)
        every { notificationPreferences.briefingMorningHour } returns flowOf(8)

        val result = projector.projectAll(nowMillis = now)

        assertEquals(7, result.size)
        result.forEach {
            assertEquals(ProjectedNotification.Source.BRIEFING, it.source)
            assertEquals("Good Morning", it.title)
        }
    }

    @Test
    fun `weekly review projects only when both auto-generate and notification flags enabled`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        // Auto-generate ON but notification OFF — should not project
        every { notificationPreferences.weeklyReviewAutoGenerateEnabled } returns flowOf(true)
        every { notificationPreferences.weeklyReviewNotificationEnabled } returns flowOf(false)

        val result1 = projector.projectAll(nowMillis = now)
        assertFalse(result1.any { it.source == ProjectedNotification.Source.WEEKLY_REVIEW })

        // Both ON — should project the next Sunday at 8 PM (within horizon)
        every { notificationPreferences.weeklyReviewNotificationEnabled } returns flowOf(true)
        val result2 = projector.projectAll(nowMillis = now)
        assertNotNull(result2.firstOrNull { it.source == ProjectedNotification.Source.WEEKLY_REVIEW })
    }

    @Test
    fun `worker projection respects disabled toggles`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 10, 0)
        // All workers OFF by default
        val result = projector.projectAll(nowMillis = now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed sources are sorted chronologically`() = runBlocking {
        val now = baseInstant(2026, Calendar.MAY, 1, 6, 0)
        coEvery { habitDao.getHabitsWithDailyTimeReminder() } returns listOf(
            habit(id = 1L, name = "Morning", reminderTime = 9 * 60 * 60 * 1000L),
            habit(id = 2L, name = "Evening", reminderTime = 20 * 60 * 60 * 1000L)
        )
        every { notificationPreferences.dailyBriefingEnabled } returns flowOf(true)
        every { notificationPreferences.briefingMorningHour } returns flowOf(7)

        val result = projector.projectAll(nowMillis = now)

        assertTrue(result.size >= 14)
        for (i in 1 until result.size) {
            assertTrue(result[i].triggerAtMillis >= result[i - 1].triggerAtMillis)
        }
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
        reminderTime: Long? = null,
        reminderIntervalMillis: Long? = null,
        reminderTimesPerDay: Int = 1
    ): HabitEntity = HabitEntity(
        id = id,
        name = name,
        description = null,
        reminderTime = reminderTime,
        reminderIntervalMillis = reminderIntervalMillis,
        reminderTimesPerDay = reminderTimesPerDay
    )

    private fun completion(habitId: Long, completedAt: Long): HabitCompletionEntity =
        HabitCompletionEntity(
            id = 0,
            habitId = habitId,
            completedDate = completedAt,
            completedDateLocal = "2026-05-01",
            completedAt = completedAt
        )

    private fun medication(
        id: Long,
        name: String,
        scheduleMode: String,
        timesOfDay: String? = null,
        specificTimes: String? = null,
        intervalMillis: Long? = null
    ): MedicationEntity = MedicationEntity(
        id = id,
        name = name,
        scheduleMode = scheduleMode,
        timesOfDay = timesOfDay,
        specificTimes = specificTimes,
        intervalMillis = intervalMillis
    )

    private fun dose(medicationId: Long, takenAt: Long): MedicationDoseEntity =
        MedicationDoseEntity(
            id = 0,
            medicationId = medicationId,
            slotKey = "interval",
            takenAt = takenAt,
            takenDateLocal = "2026-05-01"
        )
}
