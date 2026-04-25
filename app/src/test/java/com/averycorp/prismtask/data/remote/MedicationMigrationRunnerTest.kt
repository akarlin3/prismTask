package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.preferences.MedicationMigrationPreferences
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.notifications.HabitReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit coverage for [MedicationMigrationRunner]'s schedule-preservation +
 * legacy-scheduler-disarm passes. Runs pure-JVM with in-memory DAO
 * fakes + mocked DataStore preferences, so no Room/emulator setup
 * required.
 *
 * Covers the PR 1 duplicate-alarms-fix branch — without these tests the
 * regression risk on PR #639's disarmLegacyScheduler logic would sit
 * untested.
 */
class MedicationMigrationRunnerTest {
    private lateinit var medicationDao: RunnerFakeMedDao
    private lateinit var habitDao: HabitDao
    private lateinit var selfCareDao: SelfCareDao
    private lateinit var medicationPreferences: MedicationPreferences
    private lateinit var migrationPreferences: MedicationMigrationPreferences
    private lateinit var habitReminderScheduler: HabitReminderScheduler
    private lateinit var syncTracker: SyncTracker
    private lateinit var runner: MedicationMigrationRunner

    @Before
    fun setUp() {
        medicationDao = RunnerFakeMedDao()
        habitDao = mockk(relaxed = true)
        selfCareDao = mockk(relaxed = true)
        medicationPreferences = mockk(relaxed = true)
        migrationPreferences = mockk(relaxed = true)
        habitReminderScheduler = mockk(relaxed = true)
        syncTracker = mockk(relaxed = true)

        coEvery { migrationPreferences.isSchedulePreserved() } returns false
        coEvery { migrationPreferences.isDoseBackfillDone() } returns false
        coEvery { medicationPreferences.getSpecificTimesOnce() } returns emptySet()
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.INTERVAL
        coEvery { medicationPreferences.getReminderIntervalMinutesOnce() } returns 0
        coEvery { habitDao.getHabitByName(any()) } returns null

        runner = MedicationMigrationRunner(
            medicationDao = medicationDao,
            medicationDoseDao = mockk(relaxed = true),
            habitDao = habitDao,
            selfCareDao = selfCareDao,
            medicationPreferences = medicationPreferences,
            migrationPreferences = migrationPreferences,
            syncTracker = syncTracker,
            habitReminderScheduler = habitReminderScheduler,
            logger = mockk(relaxed = true)
        )
    }

    @Test
    fun preserveSchedule_noOpsWhenFlagAlreadySet() = runBlocking {
        coEvery { migrationPreferences.isSchedulePreserved() } returns true
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")

        runner.preserveScheduleIfNeeded()

        coVerify(exactly = 0) { migrationPreferences.setSchedulePreserved(any()) }
        // DAO not touched either
        assertEquals(1, medicationDao.rows.size)
        assertEquals("TIMES_OF_DAY", medicationDao.rows.single().scheduleMode)
    }

    @Test
    fun preserveSchedule_emptyMedicationsTableMarksFlagDone() = runBlocking {
        // No medication rows — nothing to update.
        runner.preserveScheduleIfNeeded()

        coVerify { migrationPreferences.setSchedulePreserved(true) }
    }

    @Test
    fun preserveSchedule_specificTimesModeWritesSpecificTimesOntoEveryMedication() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        medicationDao.rows += MedicationEntity(id = 2, name = "Adderall")
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.SPECIFIC_TIMES
        coEvery { medicationPreferences.getSpecificTimesOnce() } returns
            setOf("21:00", "08:00", "14:30") // unsorted on purpose

        runner.preserveScheduleIfNeeded()

        for (med in medicationDao.rows) {
            assertEquals("SPECIFIC_TIMES", med.scheduleMode)
            assertEquals(
                "specific_times must be sorted + comma-joined",
                "08:00,14:30,21:00",
                med.specificTimes
            )
            assertNull("interval must be cleared in SPECIFIC_TIMES mode", med.intervalMillis)
        }
        coVerify { migrationPreferences.setSchedulePreserved(true) }
    }

    @Test
    fun preserveSchedule_intervalModePrefersHabitReminderIntervalMillis() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.INTERVAL
        coEvery { medicationPreferences.getReminderIntervalMinutesOnce() } returns 15 // 15 min
        coEvery { habitDao.getHabitByName(SelfCareRepository.MEDICATION_HABIT_NAME) } returns
            HabitEntity(id = 10, name = "Medication", reminderIntervalMillis = 6_000_000L)

        runner.preserveScheduleIfNeeded()

        val med = medicationDao.rows.single()
        assertEquals("INTERVAL", med.scheduleMode)
        assertEquals(
            "habit.reminderIntervalMillis wins over prefs when both are set",
            6_000_000L,
            med.intervalMillis
        )
    }

    @Test
    fun preserveSchedule_intervalModeFallsBackToPrefsWhenHabitIntervalNull() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.INTERVAL
        coEvery { medicationPreferences.getReminderIntervalMinutesOnce() } returns 30
        coEvery { habitDao.getHabitByName(SelfCareRepository.MEDICATION_HABIT_NAME) } returns
            HabitEntity(id = 10, name = "Medication", reminderIntervalMillis = null)

        runner.preserveScheduleIfNeeded()

        val med = medicationDao.rows.single()
        assertEquals(
            "30 minutes = 1_800_000 ms",
            30 * 60_000L,
            med.intervalMillis
        )
    }

    @Test
    fun preserveSchedule_intervalModeWithNoIntervalAnywhereLeavesRowUnchanged() = runBlocking {
        // User's schedule mode says INTERVAL but neither the habit nor
        // prefs actually hold an interval — keep the row as migration
        // wrote it (TIMES_OF_DAY default) rather than silently clearing.
        val original = MedicationEntity(id = 1, name = "Lipitor")
        medicationDao.rows += original
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.INTERVAL
        coEvery { medicationPreferences.getReminderIntervalMinutesOnce() } returns 0
        coEvery { habitDao.getHabitByName(any()) } returns null

        runner.preserveScheduleIfNeeded()

        val med = medicationDao.rows.single()
        assertEquals("TIMES_OF_DAY", med.scheduleMode)
        assertNull(med.intervalMillis)
    }

    @Test
    fun preserveSchedule_disarmsLegacyScheduler_cancelsSpecificTimeAlarms() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.SPECIFIC_TIMES
        coEvery { medicationPreferences.getSpecificTimesOnce() } returns
            setOf("08:00", "14:00", "21:00")

        runner.preserveScheduleIfNeeded()

        // Scheduler was asked to cancel indices 0..12 (3 active + 10 buffer).
        verify(atLeast = 3) {
            habitReminderScheduler.cancelSpecificTime(any())
        }
        // Prefs cleared so next boot doesn't reschedule.
        coVerify { medicationPreferences.setSpecificTimes(emptySet()) }
    }

    @Test
    fun preserveSchedule_disarmsLegacyScheduler_nullsBuiltInHabitReminderFields() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        // reminderTime is millis-since-midnight; 28_800_000 = 08:00.
        val builtIn = HabitEntity(
            id = 10,
            name = "Medication",
            reminderIntervalMillis = 6_000_000L,
            reminderTime = 28_800_000L
        )
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.INTERVAL
        coEvery { habitDao.getHabitByName(SelfCareRepository.MEDICATION_HABIT_NAME) } returns builtIn

        val updateSlot = slot<HabitEntity>()
        coEvery { habitDao.update(capture(updateSlot)) } returns Unit

        runner.preserveScheduleIfNeeded()

        // Alarm cancellation fired for both the +200_000 and +900_000 paths.
        verify { habitReminderScheduler.cancelAll(10L) }
        // Habit update nulled both reminder fields.
        assertNull(updateSlot.captured.reminderIntervalMillis)
        assertNull(updateSlot.captured.reminderTime)
        coVerify { syncTracker.trackUpdate(10L, "habit") }
    }

    @Test
    fun preserveSchedule_disarmsLegacyScheduler_skipsHabitUpdateWhenFieldsAlreadyNull() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        coEvery { medicationPreferences.getScheduleModeOnce() } returns
            MedicationScheduleMode.INTERVAL
        coEvery { habitDao.getHabitByName(SelfCareRepository.MEDICATION_HABIT_NAME) } returns
            HabitEntity(
                id = 10,
                name = "Medication",
                reminderIntervalMillis = null,
                reminderTime = null
            )

        runner.preserveScheduleIfNeeded()

        // No reminder fields to null → no habit update / trackUpdate.
        coVerify(exactly = 0) { habitDao.update(any()) }
        coVerify(exactly = 0) { syncTracker.trackUpdate(any(), eq("habit")) }
    }

    @Test
    fun preserveSchedule_exceptionKeepsFlagFalseForRetry() = runBlocking {
        medicationDao.rows += MedicationEntity(id = 1, name = "Lipitor")
        // Simulate a DataStore read throwing mid-flight.
        coEvery { medicationPreferences.getScheduleModeOnce() } throws
            RuntimeException("datastore offline")

        runner.preserveScheduleIfNeeded()

        // Flag NOT set — next app start will retry.
        coVerify(exactly = 0) { migrationPreferences.setSchedulePreserved(any()) }
    }
}

// --- in-memory fake med DAO -------------------------------------------

private class RunnerFakeMedDao : MedicationDao {
    val rows = mutableListOf<MedicationEntity>()
    private var nextId = 100L

    override suspend fun insert(medication: MedicationEntity): Long {
        val id = if (medication.id == 0L) nextId++ else medication.id
        rows += medication.copy(id = id)
        return id
    }

    override suspend fun update(medication: MedicationEntity) {
        val idx = rows.indexOfFirst { it.id == medication.id }
        if (idx >= 0) rows[idx] = medication
    }

    override suspend fun archive(id: Long, now: Long) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(isArchived = true, updatedAt = now)
    }

    override suspend fun delete(medication: MedicationEntity) {
        rows.removeAll { it.id == medication.id }
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun getByIdOnce(id: Long): MedicationEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun getByNameOnce(name: String): MedicationEntity? =
        rows.firstOrNull { it.name == name }

    override suspend fun getActiveOnce(): List<MedicationEntity> =
        rows.filter { !it.isArchived }

    override suspend fun getAllOnce(): List<MedicationEntity> = rows.toList()

    override suspend fun getByCloudIdOnce(cloudId: String): MedicationEntity? =
        rows.firstOrNull { it.cloudId == cloudId }

    override suspend fun setCloudId(id: Long, cloudId: String?, now: Long) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(cloudId = cloudId, updatedAt = now)
    }

    override fun getActive() = error("flow not exercised")
    override fun getAll() = error("flow not exercised")
    override fun observeById(id: Long) = error("flow not exercised")

    override suspend fun getIntervalModeMedicationsOnce(): List<MedicationEntity> =
        rows.filter { !it.isArchived && it.reminderMode == "INTERVAL" }
}
