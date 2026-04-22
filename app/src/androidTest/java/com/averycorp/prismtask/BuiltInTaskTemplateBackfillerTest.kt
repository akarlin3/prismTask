package com.averycorp.prismtask

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.BuiltInTaskTemplateBackfiller
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for [BuiltInTaskTemplateBackfiller]. Plants pulled-from-
 * Firestore task_templates rows that have `template_key = NULL` and/or
 * `is_built_in = false` and verifies the pass heals rows that match a
 * built-in by name while leaving user content alone.
 */
@RunWith(AndroidJUnit4::class)
class BuiltInTaskTemplateBackfillerTest {
    private lateinit var database: PrismTaskDatabase
    private lateinit var backfiller: BuiltInTaskTemplateBackfiller
    private lateinit var builtInSyncPreferences: BuiltInSyncPreferences
    private lateinit var syncTracker: SyncTracker
    private lateinit var logger: PrismSyncLogger

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        builtInSyncPreferences = mockk(relaxed = true)
        coEvery { builtInSyncPreferences.isTaskTemplateBackfillDone() } returns false

        syncTracker = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        backfiller = BuiltInTaskTemplateBackfiller(
            taskTemplateDao = database.taskTemplateDao(),
            syncTracker = syncTracker,
            builtInSyncPreferences = builtInSyncPreferences,
            logger = logger
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun backfill_matchesByExactName_setsTemplateKeyAndIsBuiltIn() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = null,
                isBuiltIn = false,
                cloudId = "pulled-cloud-id"
            )
        )

        backfiller.runBackfillIfNeeded()

        val row = dao.getTemplateById(id)!!
        assertEquals("builtin_weekly_review", row.templateKey)
        assertTrue("is_built_in should be set", row.isBuiltIn)
        assertEquals("cloud_id should be preserved", "pulled-cloud-id", row.cloudId)
    }

    @Test
    fun backfill_matchesCaseInsensitive() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "weekly review",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        val row = dao.getTemplateById(id)!!
        assertEquals("builtin_weekly_review", row.templateKey)
    }

    @Test
    fun backfill_trimsWhitespaceDuringMatch() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "  Grocery Run  ",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        val row = dao.getTemplateById(id)!!
        assertEquals("builtin_grocery_run", row.templateKey)
    }

    @Test
    fun backfill_skipsRowsWithUsageCount() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = null,
                isBuiltIn = false,
                usageCount = 3
            )
        )

        backfiller.runBackfillIfNeeded()

        val row = dao.getTemplateById(id)!!
        assertNull("user-touched row should NOT be healed", row.templateKey)
        assertFalse("user-touched row should stay not-built-in", row.isBuiltIn)
    }

    @Test
    fun backfill_skipsRowsWithLastUsedAt() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Meeting Prep",
                templateKey = null,
                isBuiltIn = false,
                lastUsedAt = 123456L
            )
        )

        backfiller.runBackfillIfNeeded()

        val row = dao.getTemplateById(id)!!
        assertNull(row.templateKey)
    }

    @Test
    fun backfill_leavesUnmatchedRowsAlone() = runTest {
        val dao = database.taskTemplateDao()
        val userRowId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "My Custom Template",
                templateKey = null,
                isBuiltIn = false
            )
        )
        val builtInRowId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "School Daily",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        assertNull("user row should NOT be healed", dao.getTemplateById(userRowId)!!.templateKey)
        assertEquals("builtin_school_daily", dao.getTemplateById(builtInRowId)!!.templateKey)
    }

    @Test
    fun backfill_queuesUpdateForSyncTracker() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Leisure Time",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        coVerify(exactly = 1) { syncTracker.trackUpdate(id, "task_template") }
    }

    @Test
    fun backfill_resetsReconcilerFlagWhenUpdatesHappened() = runTest {
        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Meeting Prep",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        coVerify { builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(false) }
    }

    @Test
    fun backfill_doesNotResetReconcilerFlagWhenNothingHealed() = runTest {
        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Unmatched Name",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        coVerify(exactly = 0) { builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(any()) }
    }

    @Test
    fun backfill_setsDoneFlagOnSuccess() = runTest {
        backfiller.runBackfillIfNeeded()

        coVerify { builtInSyncPreferences.setTaskTemplateBackfillDone(true) }
    }

    @Test
    fun backfill_isIdempotentWhenFlagAlreadySet() = runTest {
        coEvery { builtInSyncPreferences.isTaskTemplateBackfillDone() } returns true

        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()

        assertNull(
            "flag=true should short-circuit; no updates",
            dao.getTemplateById(id)!!.templateKey
        )
        coVerify(exactly = 0) { syncTracker.trackUpdate(any(), any()) }
    }

    @Test
    fun backfill_withAlreadyTemplateKeySet_leavesRowAlone() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "some_other_key",
                isBuiltIn = true
            )
        )
        val before = dao.getTemplateById(id)!!

        backfiller.runBackfillIfNeeded()

        val after = dao.getTemplateById(id)!!
        assertEquals("already-keyed row should be untouched", before.templateKey, after.templateKey)
        assertEquals(before.updatedAt, after.updatedAt)
    }

    @Test
    fun backfill_preservesOtherColumnsDuringUpdate() = runTest {
        val dao = database.taskTemplateDao()
        val id = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Grocery Run",
                description = "User edited description",
                icon = "🛍️",
                category = "Shopping",
                templateKey = null,
                isBuiltIn = false,
                cloudId = "pulled-xyz",
                templateTagsJson = """["tag1","tag2"]"""
            )
        )

        backfiller.runBackfillIfNeeded()

        val row = dao.getTemplateById(id)!!
        assertEquals("User edited description", row.description)
        assertEquals("🛍️", row.icon)
        assertEquals("Shopping", row.category)
        assertEquals("pulled-xyz", row.cloudId)
        assertEquals("""["tag1","tag2"]""", row.templateTagsJson)
        assertNotNull("healed row should now have templateKey", row.templateKey)
    }
}
