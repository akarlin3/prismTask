package com.averycorp.prismtask

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.BuiltInTaskTemplateReconciler
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for [BuiltInTaskTemplateReconciler]. Plants duplicate
 * built-in templates that share a `templateKey`, runs the reconciler, and
 * verifies that exactly one row per key survives, that the winner owns any
 * inherited cloud_id mapping, and that the pass is idempotent.
 */
@RunWith(AndroidJUnit4::class)
class BuiltInTaskTemplateReconcilerTest {
    private lateinit var database: PrismTaskDatabase
    private lateinit var reconciler: BuiltInTaskTemplateReconciler
    private lateinit var builtInSyncPreferences: BuiltInSyncPreferences
    private lateinit var logger: PrismSyncLogger

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Relaxed mocks — the reconciler reads the "done" flag once at entry
        // and writes it once at the end; we control the read and assert on
        // the write in the idempotency test.
        builtInSyncPreferences = mockk(relaxed = true)
        coEvery { builtInSyncPreferences.isBuiltInTaskTemplatesReconciled() } returns false

        logger = mockk(relaxed = true)

        reconciler = BuiltInTaskTemplateReconciler(
            taskTemplateDao = database.taskTemplateDao(),
            syncMetadataDao = database.syncMetadataDao(),
            builtInSyncPreferences = builtInSyncPreferences,
            logger = logger
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reconcile_collapsesDuplicatesSharingSameTemplateKey() = runTest {
        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "builtin_weekly_review",
                isBuiltIn = true
            )
        )
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "builtin_weekly_review",
                isBuiltIn = true
            )
        )
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "builtin_weekly_review",
                isBuiltIn = true
            )
        )

        reconciler.reconcileAfterSyncIfNeeded()

        val survivors = dao.getBuiltInTemplatesOnce()
        assertEquals("exactly one built-in per template_key should survive", 1, survivors.size)
        assertEquals("builtin_weekly_review", survivors.single().templateKey)
    }

    @Test
    fun reconcile_picksLexSmallestCloudIdAsKeeper() = runTest {
        val dao = database.taskTemplateDao()
        val loserId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Meeting Prep",
                templateKey = "builtin_meeting_prep",
                isBuiltIn = true,
                cloudId = "zzz-loser"
            )
        )
        val keeperId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Meeting Prep",
                templateKey = "builtin_meeting_prep",
                isBuiltIn = true,
                cloudId = "aaa-keeper"
            )
        )

        reconciler.reconcileAfterSyncIfNeeded()

        val survivors = dao.getBuiltInTemplatesOnce()
        assertEquals(1, survivors.size)
        assertEquals("aaa-keeper", survivors.single().cloudId)
        assertEquals(keeperId, survivors.single().id)
        assertNull("loser row should be deleted", dao.getTemplateById(loserId))
    }

    @Test
    fun reconcile_transfersLoserCloudIdMappingWhenKeeperHasNone() = runTest {
        val dao = database.taskTemplateDao()
        val metaDao = database.syncMetadataDao()
        val keeperId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Grocery Run",
                templateKey = "builtin_grocery_run",
                isBuiltIn = true
            )
        )
        val loserId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Grocery Run",
                templateKey = "builtin_grocery_run",
                isBuiltIn = true
            )
        )
        metaDao.upsert(
            SyncMetadataEntity(
                localId = loserId,
                entityType = "task_template",
                cloudId = "cloud-from-loser",
                lastSyncedAt = 1L
            )
        )

        reconciler.reconcileAfterSyncIfNeeded()

        val transferred = metaDao.getCloudId(keeperId, "task_template")
        assertEquals("cloud-from-loser", transferred)
        assertNull("loser's metadata row should be deleted", metaDao.get(loserId, "task_template"))
    }

    @Test
    fun reconcile_leavesDistinctKeysAlone() = runTest {
        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Meeting Prep",
                templateKey = "builtin_meeting_prep",
                isBuiltIn = true
            )
        )
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Grocery Run",
                templateKey = "builtin_grocery_run",
                isBuiltIn = true
            )
        )
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "builtin_weekly_review",
                isBuiltIn = true
            )
        )

        reconciler.reconcileAfterSyncIfNeeded()

        assertEquals(3, dao.getBuiltInTemplatesOnce().size)
    }

    @Test
    fun reconcile_skipsWhenFlagAlreadySet() = runTest {
        coEvery { builtInSyncPreferences.isBuiltInTaskTemplatesReconciled() } returns true

        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "School Daily",
                templateKey = "builtin_school_daily",
                isBuiltIn = true
            )
        )
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "School Daily",
                templateKey = "builtin_school_daily",
                isBuiltIn = true
            )
        )

        reconciler.reconcileAfterSyncIfNeeded()

        assertEquals(
            "already-reconciled flag should short-circuit the pass",
            2,
            dao.getBuiltInTemplatesOnce().size
        )
        coVerify(exactly = 0) { builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(any()) }
    }

    @Test
    fun reconcile_ignoresTemplatesWithoutATemplateKey() = runTest {
        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Custom Template",
                templateKey = null,
                isBuiltIn = true
            )
        )
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Custom Template",
                templateKey = null,
                isBuiltIn = true
            )
        )

        reconciler.reconcileAfterSyncIfNeeded()

        assertEquals(
            "rows with null templateKey are not subject to dedupe",
            2,
            dao.getBuiltInTemplatesOnce().size
        )
    }

    @Test
    fun reconcile_setsDoneFlagOnSuccess() = runTest {
        reconciler.reconcileAfterSyncIfNeeded()
        coVerify { builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(true) }
    }
}
