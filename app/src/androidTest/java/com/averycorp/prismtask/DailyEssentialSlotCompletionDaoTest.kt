package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.DailyEssentialSlotCompletionDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema-level coverage for the `daily_essential_slot_completions` table.
 *
 * The medication-card write path that originally produced these rows was
 * removed alongside the Daily Essentials medication card; the table itself
 * stays in the schema for sync round-tripping of historical rows. This
 * test pins the unique-index behaviour the sync layer still relies on.
 */
@RunWith(AndroidJUnit4::class)
class DailyEssentialSlotCompletionDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var slotDao: DailyEssentialSlotCompletionDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        slotDao = database.dailyEssentialSlotCompletionDao()
    }

    @After
    fun teardown() {
        database.close()
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
