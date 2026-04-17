package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.DailyEssentialSlotCompletionDao
import com.averycorp.prismtask.data.local.dao.MedicationRefillDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import com.averycorp.prismtask.data.repository.DailyEssentialSlotCompletionRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the virtual → materialized transition for a Daily Essentials
 * medication time slot. The parent medication entity
 * ([MedicationRefillEntity]) is inserted before the child completion row to
 * prove the slot completion table is usable alongside a real medication
 * definition, not just in isolation.
 */
@RunWith(AndroidJUnit4::class)
class DailyEssentialSlotCompletionDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var slotDao: DailyEssentialSlotCompletionDao
    private lateinit var medRefillDao: MedicationRefillDao
    private lateinit var repo: DailyEssentialSlotCompletionRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        slotDao = database.dailyEssentialSlotCompletionDao()
        medRefillDao = database.medicationRefillDao()
        repo = DailyEssentialSlotCompletionRepository(slotDao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun virtualToMaterializedTransition() = runTest {
        // Parent medication entity — must exist before we reference its
        // synthetic dose key from a slot completion row.
        medRefillDao.upsert(
            MedicationRefillEntity(
                medicationName = "Lipitor",
                pillCount = 90,
                pillsPerDose = 1,
                dosesPerDay = 1,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        val dayStart = 1_700_000_000_000L
        val slotKey = "08:00"
        val doseKey = "self_care_step:lipitor"

        // Virtual phase: no row exists yet, DAO reads return empty.
        assertTrue(slotDao.getForDateOnce(dayStart).isEmpty())
        assertNull(slotDao.getBySlotOnce(dayStart, slotKey))

        // First interaction materializes the row with taken_at stamped.
        val materialized = repo.toggleSlot(
            date = dayStart,
            slotKey = slotKey,
            doseKeys = listOf(doseKey),
            taken = true,
            now = 1_700_000_100_000L
        )

        val fetched = slotDao.getBySlotOnce(dayStart, slotKey)
        assertNotNull(fetched)
        assertEquals(slotKey, fetched!!.slotKey)
        assertEquals(1_700_000_100_000L, fetched.takenAt)
        assertTrue(fetched.medIdsJson.contains(doseKey))
        assertEquals(materialized.slotKey, fetched.slotKey)

        // Toggling "taken = false" clears takenAt but keeps the row (and the
        // med_ids snapshot) so the virtual list can still show the slot.
        repo.toggleSlot(
            date = dayStart,
            slotKey = slotKey,
            doseKeys = listOf(doseKey),
            taken = false,
            now = 1_700_000_200_000L
        )
        val cleared = slotDao.getBySlotOnce(dayStart, slotKey)
        assertNotNull(cleared)
        assertNull(cleared!!.takenAt)
        assertTrue(cleared.medIdsJson.contains(doseKey))
        assertEquals(1_700_000_200_000L, cleared.updatedAt)
    }

    @Test
    fun uniqueIndexPreventsDuplicateSlotPerDay() = runTest {
        val dayStart = 1_700_000_000_000L
        val slotKey = "anytime"
        val first = DailyEssentialSlotCompletionEntity(
            id = 0,
            date = dayStart,
            slotKey = slotKey,
            medIdsJson = "[\"a\"]",
            takenAt = null,
            createdAt = 1L,
            updatedAt = 1L
        )
        val firstId = slotDao.upsert(first)
        // REPLACE-on-conflict: re-upserting the same (date, slot_key) with a
        // different id swaps the row instead of throwing. The list-for-date
        // query should still return exactly one row.
        slotDao.upsert(first.copy(id = firstId, takenAt = 42L, updatedAt = 2L))

        val all = slotDao.getForDateOnce(dayStart)
        assertEquals(1, all.size)
        assertEquals(42L, all.first().takenAt)
    }
}
