package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.BatchUndoLogDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.BatchUndoLogEntry
import com.averycorp.prismtask.domain.model.BatchEntityType
import com.averycorp.prismtask.domain.model.BatchMutationType
import kotlinx.coroutines.flow.first
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

@RunWith(AndroidJUnit4::class)
class BatchUndoLogDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var dao: BatchUndoLogDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.batchUndoLogDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun entry(
        batchId: String,
        mutationType: BatchMutationType = BatchMutationType.RESCHEDULE,
        entityType: BatchEntityType = BatchEntityType.TASK,
        entityId: Long? = 1L,
        createdAt: Long = 1_000L,
        undoneAt: Long? = null,
        expiresAt: Long = 1_000L + DAY_MILLIS,
        commandText: String = "Move all tasks tagged work to Monday"
    ) = BatchUndoLogEntry(
        batchId = batchId,
        batchCommandText = commandText,
        entityType = entityType.name,
        entityId = entityId,
        entityCloudId = entityId?.let { "cloud_$it" },
        preStateJson = """{"id":$entityId}""",
        mutationType = mutationType.name,
        createdAt = createdAt,
        undoneAt = undoneAt,
        expiresAt = expiresAt
    )

    @Test
    fun insert_thenGetEntries_returnsRowsForBatchInOrder() = runTest {
        dao.insertAll(
            listOf(
                entry(batchId = "B1", entityId = 10L),
                entry(batchId = "B1", entityId = 20L, mutationType = BatchMutationType.DELETE),
                entry(batchId = "B2", entityId = 30L)
            )
        )

        val b1 = dao.getEntriesForBatchOnce("B1")
        assertEquals(2, b1.size)
        assertEquals(10L, b1[0].entityId)
        assertEquals(20L, b1[1].entityId)
        assertEquals("DELETE", b1[1].mutationType)

        val b2 = dao.getEntriesForBatchOnce("B2")
        assertEquals(1, b2.size)
        assertEquals(30L, b2[0].entityId)
    }

    @Test
    fun observeBatchIds_returnsDistinctOrderedNewestFirst() = runTest {
        dao.insertAll(
            listOf(
                entry(batchId = "old", createdAt = 100L),
                entry(batchId = "old", createdAt = 100L, entityId = 2L),
                entry(batchId = "mid", createdAt = 200L),
                entry(batchId = "new", createdAt = 300L)
            )
        )

        val ids = dao.observeBatchIds().first()
        assertEquals(listOf("new", "mid", "old"), ids)
    }

    @Test
    fun getMostRecentBatchIdOnce_returnsLatestByCreatedAt() = runTest {
        dao.insert(entry(batchId = "older", createdAt = 100L))
        dao.insert(entry(batchId = "newer", createdAt = 999L))

        assertEquals("newer", dao.getMostRecentBatchIdOnce())
    }

    @Test
    fun getMostRecentBatchIdOnce_emptyTable_returnsNull() = runTest {
        assertNull(dao.getMostRecentBatchIdOnce())
    }

    @Test
    fun markBatchUndone_setsTimestampOnAllRowsInBatch_skipsAlreadyUndone() = runTest {
        dao.insertAll(
            listOf(
                entry(batchId = "B1", entityId = 1L),
                entry(batchId = "B1", entityId = 2L),
                // The third row is pre-marked as already undone — markBatchUndone must skip it.
                entry(batchId = "B1", entityId = 3L, undoneAt = 500L),
                entry(batchId = "B2", entityId = 99L)
            )
        )

        val updated = dao.markBatchUndone("B1", now = 7000L)
        assertEquals("only the two not-yet-undone rows must update", 2, updated)

        val b1 = dao.getEntriesForBatchOnce("B1")
        assertEquals(7000L, b1[0].undoneAt)
        assertEquals(7000L, b1[1].undoneAt)
        assertEquals("already-undone row preserves its original timestamp", 500L, b1[2].undoneAt)
        // Other batches untouched.
        assertNull(dao.getEntriesForBatchOnce("B2").single().undoneAt)
    }

    @Test
    fun sweep_dropsExpiredAndStaleUndone_keepsRecent() = runTest {
        val now = 100_000L
        val undoneCutoff = now - 7L * DAY_MILLIS
        dao.insertAll(
            listOf(
                // Expired, never undone — gets dropped. expiresAt must be < now
                // for the sweep's `expires_at < now` predicate to match; earlier
                // versions used `1L + DAY_MILLIS` which is ~86M — well past now
                // (100_000) — and silently left the row in place.
                entry(batchId = "expired", entityId = 1L, createdAt = 1L, expiresAt = now - 1L),
                // Recent, never undone — kept.
                entry(batchId = "fresh", entityId = 2L, createdAt = now - 1000, expiresAt = now + DAY_MILLIS),
                // Undone long ago — gets dropped.
                entry(
                    batchId = "stale-undone",
                    entityId = 3L,
                    createdAt = now - 30L * DAY_MILLIS,
                    undoneAt = now - 14L * DAY_MILLIS,
                    expiresAt = now - 29L * DAY_MILLIS + DAY_MILLIS
                ),
                // Undone recently — kept (still in tail window so UI can show "undone X minutes ago").
                entry(
                    batchId = "recent-undone",
                    entityId = 4L,
                    createdAt = now - 60_000L,
                    undoneAt = now - 30_000L,
                    expiresAt = now - 60_000L + DAY_MILLIS
                )
            )
        )

        val deleted = dao.sweep(now = now, undoneCutoff = undoneCutoff)
        assertEquals(2, deleted)

        val remaining = dao.getAllOnce().map { it.batchId }.toSet()
        assertEquals(setOf("fresh", "recent-undone"), remaining)
    }

    // Boundary regression tests for the sweep predicate. The DAO uses
    // `expires_at < :now` (strict-`<`) and `undone_at < :undoneCutoff`
    // (strict-`<`). Pin both. PR #707 fixed an off-anchor mistake here
    // where a test row claimed to be expired but was actually 86M ms in
    // the future, so sweep silently kept it; these tests guard against
    // a future "fix" that flips the predicate to `<=` and quietly drops
    // rows on the boundary.

    @Test
    fun sweep_atExactExpiryBoundary_keepsRow() = runTest {
        // expires_at == now. Predicate is strict `<`, so the row must survive.
        val now = 100_000L
        val undoneCutoff = now - 7L * DAY_MILLIS
        dao.insert(entry(batchId = "boundary", entityId = 1L, createdAt = now - 1, expiresAt = now))

        val deleted = dao.sweep(now = now, undoneCutoff = undoneCutoff)

        assertEquals("strict `<` semantics — boundary row survives", 0, deleted)
        assertEquals(setOf("boundary"), dao.getAllOnce().map { it.batchId }.toSet())
    }

    @Test
    fun sweep_oneMillisPastExpiry_dropsRow() = runTest {
        // expires_at == now - 1L. Predicate matches; row must be deleted.
        // Regression-pin for PR #707 — the buggy form claimed expiresAt
        // was past `now` but was actually 86M ms in the future.
        val now = 100_000L
        val undoneCutoff = now - 7L * DAY_MILLIS
        dao.insert(entry(batchId = "past", entityId = 1L, createdAt = 1L, expiresAt = now - 1L))

        val deleted = dao.sweep(now = now, undoneCutoff = undoneCutoff)

        assertEquals(1, deleted)
        assertTrue("past-expiry row must be swept", dao.getAllOnce().isEmpty())
    }

    @Test
    fun sweep_undoneAtExactCutoff_keepsRow() = runTest {
        // undone_at == undoneCutoff. Predicate is strict `<`, so the row
        // must survive — boundary parity with the expires_at arm above.
        val now = 100_000L
        val undoneCutoff = now - 7L * DAY_MILLIS
        dao.insert(
            entry(
                batchId = "boundary-undone",
                entityId = 1L,
                createdAt = undoneCutoff - 1000L,
                undoneAt = undoneCutoff,
                expiresAt = now + DAY_MILLIS
            )
        )

        val deleted = dao.sweep(now = now, undoneCutoff = undoneCutoff)

        assertEquals("strict `<` semantics on undone_at — boundary row survives", 0, deleted)
        assertEquals(setOf("boundary-undone"), dao.getAllOnce().map { it.batchId }.toSet())
    }

    @Test
    fun insert_persistsAllNullableFields() = runTest {
        // Hard-deleted entity — entity_id and cloud_id both null.
        val id = dao.insert(
            entry(batchId = "B1", entityId = null).copy(
                entityCloudId = null,
                mutationType = BatchMutationType.ARCHIVE.name,
                entityType = BatchEntityType.PROJECT.name
            )
        )
        assertTrue(id > 0)

        val rows = dao.getEntriesForBatchOnce("B1")
        assertEquals(1, rows.size)
        assertNull(rows[0].entityId)
        assertNull(rows[0].entityCloudId)
        assertEquals("ARCHIVE", rows[0].mutationType)
        assertEquals("PROJECT", rows[0].entityType)
        assertNotNull(rows[0].preStateJson)
    }

    @Test
    fun count_reflectsTotal() = runTest {
        assertEquals(0, dao.count())
        dao.insertAll(
            listOf(
                entry(batchId = "B1"),
                entry(batchId = "B1", entityId = 2L),
                entry(batchId = "B2")
            )
        )
        assertEquals(3, dao.count())
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
