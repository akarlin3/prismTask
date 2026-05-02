package com.averycorp.prismtask.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotOverrideDao
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.preferences.MedicationReminderMode
import com.averycorp.prismtask.data.preferences.MedicationReminderModePrefs
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins the slot-Flow observer wiring on both medication reschedulers
 * (`MedicationClockRescheduler.start` and
 * `MedicationIntervalRescheduler.start`). Regression coverage for the
 * P1 wrong-slot-label-at-fire-time bug — see
 * `docs/audits/MEDICATION_SLOT_LABEL_DRIFT_AUDIT.md`.
 *
 * Verifies the contract: emitting on `MedicationSlotDao.observeAll()`
 * triggers a fresh `rescheduleAll` pass. We assert the seam by mocking
 * the slot DAO and checking that `getActiveOnce` (the first read inside
 * `rescheduleAll`) is called after each Flow emission. With
 * `getActiveOnce` returning an empty list, `rescheduleAll` exits before
 * touching AlarmManager, so we don't need to arrange cancel/setExact
 * stubs.
 *
 * Robolectric supplies the Android Context so `getSystemService` returns
 * a non-null AlarmManager — required for the rescheduler's lazy
 * `alarmManager` getter even though the empty-list path bails before
 * invoking it.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class MedicationSlotFlowReschedulerTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        testScope.coroutineContext[Job]?.cancel()
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun stubUserPrefs(): UserPreferencesDataStore = mockk(relaxed = true) {
        every { medicationReminderModeFlow } returns flowOf(
            MedicationReminderModePrefs(
                mode = MedicationReminderMode.CLOCK,
                intervalDefaultMinutes = 240
            )
        )
    }

    private fun newSlot(
        id: Long = 1L,
        name: String = "Morning",
        idealTime: String = "08:00"
    ): MedicationSlotEntity = MedicationSlotEntity(
        id = id,
        name = name,
        idealTime = idealTime
    )

    @Test
    fun clockRescheduler_slotEmissionTriggersReschedulePass() = runBlocking {
        val slotsFlow = MutableSharedFlow<List<MedicationSlotEntity>>(replay = 0)
        val medsFlow = MutableSharedFlow<List<com.averycorp.prismtask.data.local.entity.MedicationEntity>>(
            replay = 0
        )
        val overridesFlow =
            MutableSharedFlow<List<com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity>>(
            replay = 0
        )
        val slotDao: MedicationSlotDao = mockk(relaxed = true) {
            every { observeAll() } returns slotsFlow
            coEvery { getActiveOnce() } returns emptyList()
        }
        val medDao: MedicationDao = mockk(relaxed = true) {
            every { getAll() } returns medsFlow
            coEvery { getActiveOnce() } returns emptyList()
        }
        val overrideDao: MedicationSlotOverrideDao = mockk(relaxed = true) {
            every { observeAll() } returns overridesFlow
            coEvery { getAllOnce() } returns emptyList()
        }
        val rescheduler = MedicationClockRescheduler(
            context = appContext(),
            medicationDao = medDao,
            medicationSlotDao = slotDao,
            medicationSlotOverrideDao = overrideDao,
            userPreferences = stubUserPrefs()
        )

        rescheduler.start(testScope)
        // Allow the launchIn-launched collector to subscribe before
        // emitting; SharedFlow with replay=0 drops emits without
        // subscribers.
        yield()

        slotsFlow.emit(listOf(newSlot(name = "Evening", idealTime = "20:00")))
        yield()
        coVerify(exactly = 1) { slotDao.getActiveOnce() }

        // A subsequent emission (e.g. sync-pulled rename) re-runs reschedule.
        slotsFlow.emit(listOf(newSlot(name = "Morning", idealTime = "08:00")))
        yield()
        coVerify(exactly = 2) { slotDao.getActiveOnce() }

        // Override-only edit (different table) also triggers reschedule —
        // the per-(med, slot) audit fix's whole point.
        overridesFlow.emit(emptyList())
        yield()
        coVerify(exactly = 3) { slotDao.getActiveOnce() }

        // Med edit (e.g. flipping reminderMode) also triggers reschedule.
        medsFlow.emit(emptyList())
        yield()
        coVerify(exactly = 4) { slotDao.getActiveOnce() }
    }

    @Test
    fun intervalRescheduler_slotEmissionTriggersReschedulePass() = runBlocking {
        val slotsFlow = MutableSharedFlow<List<MedicationSlotEntity>>(replay = 0)
        val dosesFlow = MutableSharedFlow<MedicationDoseEntity?>(replay = 0)

        val slotDao: MedicationSlotDao = mockk(relaxed = true) {
            every { observeAll() } returns slotsFlow
            coEvery { getActiveOnce() } returns emptyList()
        }
        val medDao: MedicationDao = mockk(relaxed = true) {
            coEvery { getActiveOnce() } returns emptyList()
        }
        val doseDao: MedicationDoseDao = mockk(relaxed = true) {
            every { observeMostRecentDoseAny() } returns dosesFlow
            coEvery { getMostRecentDoseAnyOnce() } returns null
        }

        val rescheduler = MedicationIntervalRescheduler(
            context = appContext(),
            medicationDao = medDao,
            medicationSlotDao = slotDao,
            medicationDoseDao = doseDao,
            userPreferences = stubUserPrefs()
        )

        rescheduler.start(testScope)
        yield()

        slotsFlow.emit(listOf(newSlot(name = "Evening", idealTime = "20:00")))
        yield()
        // rescheduleAll() reads the slot DAO once per pass.
        coVerify(exactly = 1) { slotDao.getActiveOnce() }

        // Dose emission ALSO triggers reschedule (existing behaviour, now
        // guarded by this same test fixture so we don't regress it).
        dosesFlow.emit(null)
        yield()
        coVerify(exactly = 2) { slotDao.getActiveOnce() }
    }
}
