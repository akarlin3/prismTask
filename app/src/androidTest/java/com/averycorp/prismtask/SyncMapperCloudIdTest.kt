package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that [SyncMapper.mapToX] now threads the Firestore document ID
 * through to the entity's `cloudId` column, and that a full round-trip
 * through Room (map → update → re-read) preserves the value.
 *
 * Regression coverage for the Phase 2.5 fix: prior to this patch,
 * `mapToTask` / `mapToTag` / ... silently dropped `cloudId`, every pull
 * cycle overwrote the Room row with `cloudId = null`, and
 * Migration_51_52's backfill was effectively erased on first sync.
 *
 * Covers tasks (baseline shape) and tags (the most-duplicated entity
 * in the production corruption, so particularly valuable to lock down).
 */
@RunWith(AndroidJUnit4::class)
class SyncMapperCloudIdTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var tagDao: TagDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        tagDao = database.tagDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun mapToTask_populatesCloudId_andSurvivesRoomRoundTrip() = runTest {
        // Seed: a task already in Room with a known cloudId (simulating a
        // row previously populated by Migration_51_52 or a prior pull).
        val seededId = taskDao.insert(
            TaskEntity(
                title = "Original title",
                cloudId = "remote-abc-123"
            )
        )
        assertEquals("remote-abc-123", taskDao.getTaskByIdOnce(seededId)?.cloudId)

        // Simulate an incoming Firestore pull: doc.id = "remote-abc-123",
        // body has an updated title. SyncMapper.mapToTask must thread
        // cloudId through so the subsequent taskDao.update doesn't null
        // out the column.
        val firestoreData: Map<String, Any?> = mapOf(
            "title" to "Updated from cloud",
            "isCompleted" to false,
            "priority" to 2,
            "createdAt" to 1_000L,
            "updatedAt" to 2_000L
        )
        val mapped = SyncMapper.mapToTask(
            firestoreData,
            localId = seededId,
            cloudId = "remote-abc-123"
        )
        assertEquals(
            "mapper must place cloudId from the parameter onto the entity",
            "remote-abc-123",
            mapped.cloudId
        )
        assertEquals("Updated from cloud", mapped.title)

        // Write through Room (the actual regression point: @Update would
        // have nulled cloud_id before this patch).
        taskDao.update(mapped)

        // Re-read and confirm cloudId survived the write.
        val reread = taskDao.getTaskByIdOnce(seededId)
        assertNotNull(reread)
        assertEquals(
            "cloudId must still be set after an @Update with the mapped entity",
            "remote-abc-123",
            reread?.cloudId
        )
        assertEquals("Updated from cloud", reread?.title)
    }

    @Test
    fun mapToTag_populatesCloudId_andSurvivesRoomRoundTrip() = runTest {
        val seededId = tagDao.insert(
            TagEntity(name = "work", cloudId = "cloud_work_Z9")
        )
        assertEquals("cloud_work_Z9", getTagCloudId(seededId))

        val firestoreData: Map<String, Any?> = mapOf(
            "name" to "work-renamed",
            "color" to "#123456",
            "createdAt" to 500L
        )
        val mapped = SyncMapper.mapToTag(
            firestoreData,
            localId = seededId,
            cloudId = "cloud_work_Z9"
        )
        assertEquals("cloud_work_Z9", mapped.cloudId)
        assertEquals("work-renamed", mapped.name)

        tagDao.update(mapped)

        assertEquals(
            "cloudId must still be set after an @Update on tags",
            "cloud_work_Z9",
            getTagCloudId(seededId)
        )
    }

    @Test
    fun mapToTask_defaultCloudId_isNull_whenParameterOmitted() = runTest {
        // Sanity: omitting cloudId (e.g. existing unit-test call sites
        // that never passed one) leaves the entity cloudId null — doesn't
        // quietly set some sentinel.
        val mapped = SyncMapper.mapToTask(
            mapOf("title" to "no cloud", "isCompleted" to false),
            localId = 0
        )
        assertNull(mapped.cloudId)
    }

    private suspend fun getTagCloudId(id: Long): String? =
        tagDao.getTagByIdOnce(id)?.cloudId
}
