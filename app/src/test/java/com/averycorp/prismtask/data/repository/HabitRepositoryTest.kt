package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLastCompletion
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.database.DatabaseTransactionRunner
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.HabitLogEntity
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.notifications.MedicationReminderScheduler
import com.averycorp.prismtask.util.DayBoundary
import com.averycorp.prismtask.widget.WidgetUpdateManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end style tests for [HabitRepository]. Uses in-memory fakes for the
 * DAOs and relaxed mocks for the side-effect collaborators.
 *
 * Day-start hour is fixed at 0 so completion date normalization lines up with
 * calendar midnight, making assertions deterministic without stubbing the
 * clock.
 */
class HabitRepositoryTest {
    private lateinit var habitDao: FakeHabitDao
    private lateinit var completionDao: FakeHabitCompletionDao
    private lateinit var habitLogDao: FakeHabitLogDao
    private lateinit var taskDao: TaskDao
    private lateinit var syncTracker: SyncTracker
    private lateinit var medicationReminderScheduler: MedicationReminderScheduler
    private lateinit var taskBehaviorPreferences: TaskBehaviorPreferences
    private lateinit var habitListPreferences: HabitListPreferences
    private lateinit var widgetUpdateManager: WidgetUpdateManager
    private lateinit var repo: HabitRepository

    @Before
    fun setUp() {
        habitDao = FakeHabitDao()
        completionDao = FakeHabitCompletionDao()
        habitLogDao = FakeHabitLogDao()
        taskDao = mockk(relaxed = true)
        syncTracker = mockk(relaxed = true)
        medicationReminderScheduler = mockk(relaxed = true)
        taskBehaviorPreferences = mockk(relaxed = true)
        habitListPreferences = mockk(relaxed = true)
        widgetUpdateManager = mockk(relaxed = true)
        every { taskBehaviorPreferences.getDayStartHour() } returns flowOf(0)
        every { habitListPreferences.getStreakMaxMissedDays() } returns flowOf(1)

        repo = HabitRepository(
            inlineTransactionRunner(),
            habitDao,
            completionDao,
            habitLogDao,
            taskDao,
            syncTracker,
            medicationReminderScheduler,
            taskBehaviorPreferences,
            habitListPreferences,
            widgetUpdateManager
        )
    }

    private fun inlineTransactionRunner(): DatabaseTransactionRunner =
        object : DatabaseTransactionRunner(mockk(relaxed = true)) {
            override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
        }

    // ---------------------------------------------------------------------
    // CRUD
    // ---------------------------------------------------------------------

    @Test
    fun addHabit_storesFieldsAndTracksCreate() = runBlocking {
        val id = repo.addHabit(
            HabitEntity(
                name = "Meditate",
                description = "10 min",
                color = "#00AAFF",
                icon = "\uD83E\uDDD8",
                category = "wellness"
            )
        )

        val stored = habitDao.habits.single { it.id == id }
        assertEquals("Meditate", stored.name)
        assertEquals("10 min", stored.description)
        assertEquals("#00AAFF", stored.color)
        assertEquals("wellness", stored.category)
    }

    @Test
    fun updateHabit_bumpsUpdatedAt() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Old", updatedAt = 1L))
        val existing = habitDao.habits.single { it.id == id }

        repo.updateHabit(existing.copy(name = "New"))

        val after = habitDao.habits.single { it.id == id }
        assertEquals("New", after.name)
        assertTrue(after.updatedAt > existing.updatedAt)
    }

    @Test
    fun deleteHabit_removesHabit() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Obsolete"))
        repo.deleteHabit(id)
        assertTrue(habitDao.habits.none { it.id == id })
    }

    @Test
    fun archiveHabit_setsIsArchivedTrue() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Archive me"))
        repo.archiveHabit(id)
        assertTrue(habitDao.habits.single { it.id == id }.isArchived)
    }

    @Test
    fun unarchiveHabit_clearsIsArchived() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Back", isArchived = true))
        repo.unarchiveHabit(id)
        assertFalse(habitDao.habits.single { it.id == id }.isArchived)
    }

    @Test
    fun getActiveHabits_excludesArchived() = runBlocking {
        habitDao.insert(HabitEntity(name = "Active"))
        habitDao.insert(HabitEntity(name = "Shelved", isArchived = true))

        val active = repo.getActiveHabits().first()
        assertEquals(1, active.size)
        assertEquals("Active", active.single().name)
    }

    // ---------------------------------------------------------------------
    // Completion toggling
    // ---------------------------------------------------------------------

    @Test
    fun completeHabit_createsCompletionNormalizedToMidnight() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Drink water"))
        val noon = DayBoundary.startOfCurrentDay(0) + (12L * 60 * 60 * 1000) // midday

        repo.completeHabit(habitId = id, date = noon)

        val completions = completionDao.getCompletionsForHabitOnce(id)
        assertEquals(1, completions.size)
        assertEquals(
            DayBoundary.normalizeToDayStart(noon, 0),
            completions.single().completedDate
        )
    }

    @Test
    fun completeHabit_targetAlreadyReached_doesNotDuplicate() = runBlocking {
        val id = habitDao.insert(
            HabitEntity(name = "Once", targetFrequency = 1, frequencyPeriod = "daily")
        )
        val today = DayBoundary.startOfCurrentDay(0)

        repo.completeHabit(id, today)
        repo.completeHabit(id, today) // second call should be a no-op

        assertEquals(1, completionDao.getCompletionsForHabitOnce(id).size)
    }

    @Test
    fun completeHabit_multiTargetAllowsMultipleCompletionsPerDay() = runBlocking {
        val id = habitDao.insert(
            HabitEntity(name = "Drink water", targetFrequency = 3, frequencyPeriod = "daily")
        )
        val today = DayBoundary.startOfCurrentDay(0)

        repo.completeHabit(id, today)
        repo.completeHabit(id, today)
        repo.completeHabit(id, today)

        assertEquals(3, completionDao.getCompletionsForHabitOnce(id).size)
    }

    @Test
    fun uncompleteHabit_removesCompletionForDate() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Maybe"))
        val today = DayBoundary.startOfCurrentDay(0)
        repo.completeHabit(id, today)
        assertEquals(1, completionDao.getCompletionsForHabitOnce(id).size)

        repo.uncompleteHabit(id, today)
        assertEquals(0, completionDao.getCompletionsForHabitOnce(id).size)
    }

    // ---------------------------------------------------------------------
    // Habit logs (bookable)
    // ---------------------------------------------------------------------

    @Test
    fun logActivity_insertsLogAndTouchesHabitUpdatedAt() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Yoga", isBookable = true, updatedAt = 1L))
        val before = habitDao.habits.single { it.id == id }.updatedAt

        val logId = repo.logActivity(id, date = 100L, notes = "  focused  ")

        val logs = habitLogDao.logs.filter { it.habitId == id }
        assertEquals(1, logs.size)
        assertEquals(logId, logs.single().id)
        // Whitespace trim on notes.
        assertEquals("focused", logs.single().notes)

        val after = habitDao.habits.single { it.id == id }.updatedAt
        assertTrue("Habit updatedAt should be touched after logging", after > before)
    }

    @Test
    fun logActivity_whenBooked_clearsBookingFields() = runBlocking {
        val id = habitDao.insert(
            HabitEntity(
                name = "Appointment",
                isBookable = true,
                isBooked = true,
                bookedDate = 500L,
                bookedNote = "doctor"
            )
        )

        repo.logActivity(id, date = 1000L, notes = null)

        val updated = habitDao.habits.single { it.id == id }
        assertFalse(updated.isBooked)
        assertNull(updated.bookedDate)
        assertNull(updated.bookedNote)
    }

    @Test
    fun setBooked_storesBookingFields() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Visit", isBookable = true))

        repo.setBooked(id, isBooked = true, bookedDate = 500L, bookedNote = "dentist")

        val after = habitDao.habits.single { it.id == id }
        assertTrue(after.isBooked)
        assertEquals(500L, after.bookedDate)
        assertEquals("dentist", after.bookedNote)
    }

    @Test
    fun setBooked_false_clearsBookingFields() = runBlocking {
        val id = habitDao.insert(
            HabitEntity(
                name = "Cancelled",
                isBookable = true,
                isBooked = true,
                bookedDate = 500L,
                bookedNote = "old"
            )
        )

        repo.setBooked(id, isBooked = false, bookedDate = null, bookedNote = null)

        val after = habitDao.habits.single { it.id == id }
        assertFalse(after.isBooked)
        assertNull(after.bookedDate)
        assertNull(after.bookedNote)
    }

    @Test
    fun getLastLogDate_returnsMostRecentLogDateOrNull() = runBlocking {
        val id = habitDao.insert(HabitEntity(name = "Yoga", isBookable = true))
        assertNull(repo.getLastLogDate(id))

        habitLogDao.insertLog(HabitLogEntity(habitId = id, date = 100L))
        habitLogDao.insertLog(HabitLogEntity(habitId = id, date = 300L))
        habitLogDao.insertLog(HabitLogEntity(habitId = id, date = 200L))

        assertEquals(300L, repo.getLastLogDate(id))
    }

    // ---------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------

    private class FakeHabitDao : HabitDao {
        val habits = mutableListOf<HabitEntity>()
        private var nextId = 1L

        override suspend fun insert(habit: HabitEntity): Long {
            val id = if (habit.id == 0L) nextId++ else habit.id.also { nextId = maxOf(nextId, it + 1) }
            habits.removeAll { it.id == id }
            habits.add(habit.copy(id = id))
            return id
        }

        override suspend fun update(habit: HabitEntity) {
            val idx = habits.indexOfFirst { it.id == habit.id }
            if (idx >= 0) habits[idx] = habit
        }

        override suspend fun delete(habit: HabitEntity) {
            habits.removeAll { it.id == habit.id }
        }

        override suspend fun deleteById(id: Long) {
            habits.removeAll { it.id == id }
        }

        override suspend fun updateAll(habits: List<HabitEntity>) {
            habits.forEach { h ->
                val idx = this.habits.indexOfFirst { it.id == h.id }
                if (idx >= 0) this.habits[idx] = h
            }
        }

        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(habits.toList())

        override fun getActiveHabits(): Flow<List<HabitEntity>> =
            flowOf(habits.filter { !it.isArchived })

        override fun getHabitById(id: Long): Flow<HabitEntity?> =
            flowOf(habits.firstOrNull { it.id == id })

        override suspend fun getHabitByIdOnce(id: Long): HabitEntity? =
            habits.firstOrNull { it.id == id }

        override suspend fun getHabitByName(name: String): HabitEntity? =
            habits.firstOrNull { it.name == name }

        override suspend fun getActiveHabitsOnce(): List<HabitEntity> =
            habits.filter { !it.isArchived }

        override fun getHabitsActiveForDay(day: Int): Flow<List<HabitEntity>> =
            flowOf(
                habits.filter { habit ->
                    if (habit.isArchived) return@filter false
                    val active = habit.activeDays
                    active.isNullOrBlank() || active.contains(day.toString())
                }
            )

        override suspend fun getAllHabitsOnce(): List<HabitEntity> = habits.toList()

        override suspend fun getHabitsWithIntervalReminder(): List<HabitEntity> =
            habits.filter { it.reminderIntervalMillis != null && !it.isArchived }

        override suspend fun getAllCategories(): List<String> =
            habits.mapNotNull { it.category }.distinct().sorted()

        override suspend fun deleteAll() {
            habits.clear()
        }
    }

    private class FakeHabitCompletionDao : HabitCompletionDao {
        val completions = mutableListOf<HabitCompletionEntity>()
        private var nextId = 1L

        override suspend fun insert(completion: HabitCompletionEntity): Long {
            val id = if (completion.id == 0L) {
                nextId++
            } else {
                completion.id.also {
                    nextId = maxOf(nextId, it + 1)
                }
            }
            completions.add(completion.copy(id = id))
            return id
        }

        override fun getCompletionsForHabit(habitId: Long): Flow<List<HabitCompletionEntity>> =
            flowOf(completions.filter { it.habitId == habitId })

        override suspend fun getCompletionsForHabitOnce(habitId: Long): List<HabitCompletionEntity> =
            completions.filter { it.habitId == habitId }

        @Suppress("DEPRECATION")
        override fun getCompletionsForDate(date: Long): Flow<List<HabitCompletionEntity>> =
            flowOf(completions.filter { it.completedDate == date })

        override fun getCompletionsForDateLocal(date: String): Flow<List<HabitCompletionEntity>> =
            flowOf(completions.filter { it.completedDateLocal == date })

        override fun getCompletionsInRange(habitId: Long, startDate: Long, endDate: Long): Flow<List<HabitCompletionEntity>> =
            flowOf(completions.filter { it.habitId == habitId && it.completedDate in startDate..endDate })

        override fun getCompletionCountInRange(habitId: Long, startDate: Long, endDate: Long): Flow<Int> =
            flowOf(completions.count { it.habitId == habitId && it.completedDate in startDate..endDate })

        @Suppress("DEPRECATION")
        override fun isCompletedOnDate(habitId: Long, date: Long): Flow<Boolean> =
            flowOf(completions.any { it.habitId == habitId && it.completedDate == date })

        override fun isCompletedOnDateLocal(habitId: Long, date: String): Flow<Boolean> =
            flowOf(completions.any { it.habitId == habitId && it.completedDateLocal == date })

        @Suppress("DEPRECATION")
        override suspend fun isCompletedOnDateOnce(habitId: Long, date: Long): Boolean =
            completions.any { it.habitId == habitId && it.completedDate == date }

        override suspend fun isCompletedOnDateLocalOnce(habitId: Long, date: String): Boolean =
            completions.any { it.habitId == habitId && it.completedDateLocal == date }

        @Suppress("DEPRECATION")
        override suspend fun getCompletionCountForDateOnce(habitId: Long, date: Long): Int =
            completions.count { it.habitId == habitId && it.completedDate == date }

        override suspend fun getCompletionCountForDateLocalOnce(habitId: Long, date: String): Int =
            completions.count { it.habitId == habitId && it.completedDateLocal == date }

        @Suppress("DEPRECATION")
        override suspend fun getByHabitAndDate(habitId: Long, date: Long): HabitCompletionEntity? =
            completions.firstOrNull { it.habitId == habitId && it.completedDate == date }

        override suspend fun getByHabitAndDateLocal(habitId: Long, date: String): HabitCompletionEntity? =
            completions.firstOrNull { it.habitId == habitId && it.completedDateLocal == date }

        @Suppress("DEPRECATION")
        override suspend fun deleteByHabitAndDate(habitId: Long, date: Long) {
            completions.removeAll { it.habitId == habitId && it.completedDate == date }
        }

        override suspend fun deleteByHabitAndDateLocal(habitId: Long, date: String) {
            completions.removeAll { it.habitId == habitId && it.completedDateLocal == date }
        }

        @Suppress("DEPRECATION")
        override suspend fun deleteLatestByHabitAndDate(habitId: Long, date: Long) {
            val latest = completions
                .filter { it.habitId == habitId && it.completedDate == date }
                .maxByOrNull { it.completedAt }
            if (latest != null) completions.remove(latest)
        }

        override suspend fun deleteLatestByHabitAndDateLocal(habitId: Long, date: String) {
            val latest = completions
                .filter { it.habitId == habitId && it.completedDateLocal == date }
                .maxByOrNull { it.completedAt }
            if (latest != null) completions.remove(latest)
        }

        override fun getLastCompletion(habitId: Long): Flow<HabitCompletionEntity?> =
            flowOf(completions.filter { it.habitId == habitId }.maxByOrNull { it.completedDate })

        override fun getLastCompletionDatesPerHabit(): Flow<List<HabitLastCompletion>> =
            flowOf(
                completions
                    .groupBy { it.habitId }
                    .map { (habitId, entries) ->
                        HabitLastCompletion(habitId, entries.maxOf { it.completedDate })
                    }
            )

        override suspend fun getLastCompletionOnce(habitId: Long): HabitCompletionEntity? =
            completions.filter { it.habitId == habitId }.maxByOrNull { it.completedAt }

        override suspend fun getAllCompletionsOnce(): List<HabitCompletionEntity> =
            completions.toList()

        override suspend fun deleteAll() {
            completions.clear()
        }
    }

    private class FakeHabitLogDao : HabitLogDao {
        val logs = mutableListOf<HabitLogEntity>()
        private var nextId = 1L

        override suspend fun insertLog(log: HabitLogEntity): Long {
            val id = if (log.id == 0L) nextId++ else log.id.also { nextId = maxOf(nextId, it + 1) }
            logs.add(log.copy(id = id))
            return id
        }

        override suspend fun deleteLog(log: HabitLogEntity) {
            logs.removeAll { it.id == log.id }
        }

        override suspend fun updateLog(log: HabitLogEntity) {
            val idx = logs.indexOfFirst { it.id == log.id }
            if (idx >= 0) logs[idx] = log
        }

        override fun getLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>> =
            flowOf(logs.filter { it.habitId == habitId }.sortedByDescending { it.date })

        override fun getAllLogs(): Flow<List<HabitLogEntity>> =
            flowOf(logs.sortedByDescending { it.date })

        override suspend fun getLastLog(habitId: Long): HabitLogEntity? =
            logs.filter { it.habitId == habitId }.maxByOrNull { it.date }

        override fun getLogCount(habitId: Long): Flow<Int> =
            flowOf(logs.count { it.habitId == habitId })

        override suspend fun getAllLogsOnce(): List<HabitLogEntity> =
            logs.sortedByDescending { it.date }

        override suspend fun deleteAll() {
            logs.clear()
        }
    }
}
