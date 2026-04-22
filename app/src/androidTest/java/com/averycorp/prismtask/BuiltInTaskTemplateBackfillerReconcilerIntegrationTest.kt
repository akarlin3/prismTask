package com.averycorp.prismtask

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.BuiltInTaskTemplateBackfiller
import com.averycorp.prismtask.data.remote.BuiltInTaskTemplateReconciler
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
 * End-to-end integration test covering the full SPEC_BUILT_IN_TASK_TEMPLATE_RECONCILER
 * flow: planted state → [BuiltInTaskTemplateBackfiller] → [BuiltInTaskTemplateReconciler]
 * → final collapsed state with cloud_ids preserved.
 *
 * This is the test that validates the spec's central claim: a user with 5
 * reseeded built-ins (template_key set, cloud_id NULL) and 5 Firestore-pulled
 * siblings (template_key NULL because they predate the column) converges to
 * 5 rows where each keeper has BOTH the template_key AND the pulled cloud_id,
 * and no user content is lost.
 *
 * Firestore is not touched — the backfiller's syncTracker.trackUpdate call
 * is verified via mockk, and the reconciler is local-only.
 */
@RunWith(AndroidJUnit4::class)
class BuiltInTaskTemplateBackfillerReconcilerIntegrationTest {
    private lateinit var database: PrismTaskDatabase
    private lateinit var backfiller: BuiltInTaskTemplateBackfiller
    private lateinit var reconciler: BuiltInTaskTemplateReconciler
    private lateinit var builtInSyncPreferences: BuiltInSyncPreferences
    private lateinit var syncTracker: SyncTracker
    private lateinit var logger: PrismSyncLogger

    // Preference flag state held by hand since we're exercising the reset
    // dance between backfiller and reconciler.
    private var backfillFlagHeld = false
    private var reconciledFlagHeld = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        builtInSyncPreferences = mockk(relaxed = false)
        coEvery { builtInSyncPreferences.isTaskTemplateBackfillDone() } answers { backfillFlagHeld }
        coJustRun { builtInSyncPreferences.setTaskTemplateBackfillDone(any()) }
        val backfillSlot = slot<Boolean>()
        coEvery { builtInSyncPreferences.setTaskTemplateBackfillDone(capture(backfillSlot)) } answers {
            backfillFlagHeld = backfillSlot.captured
        }
        coEvery { builtInSyncPreferences.isBuiltInTaskTemplatesReconciled() } answers { reconciledFlagHeld }
        val reconciledSlot = slot<Boolean>()
        coEvery { builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(capture(reconciledSlot)) } answers {
            reconciledFlagHeld = reconciledSlot.captured
        }

        syncTracker = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        backfiller = BuiltInTaskTemplateBackfiller(
            taskTemplateDao = database.taskTemplateDao(),
            syncTracker = syncTracker,
            builtInSyncPreferences = builtInSyncPreferences,
            logger = logger
        )
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
    fun backfiller_then_reconciler_collapsesDuplicatesAndKeepsCloudIds() = runTest {
        val dao = database.taskTemplateDao()
        val metaDao = database.syncMetadataDao()

        // Seed state: 5 local-reseeded built-ins (template_key set, cloud_id null)
        // alongside 5 pulled-from-Firestore "twins" (template_key null, cloud_id set,
        // is_built_in=false — the shape the spec identifies as pre-column-existence).
        val reseededIds = BUILT_IN_TEMPLATES.map { (name, key) ->
            dao.insertTemplate(
                TaskTemplateEntity(
                    name = name,
                    templateKey = key,
                    isBuiltIn = true
                )
            )
        }
        val pulledIds = BUILT_IN_TEMPLATES.map { (name, _) ->
            val localId = dao.insertTemplate(
                TaskTemplateEntity(
                    name = name,
                    templateKey = null,
                    isBuiltIn = false,
                    cloudId = "firestore-doc-for-$name"
                )
            )
            metaDao.upsert(
                SyncMetadataEntity(
                    localId = localId,
                    entityType = "task_template",
                    cloudId = "firestore-doc-for-$name",
                    lastSyncedAt = 1L
                )
            )
            localId
        }

        assertEquals("10 rows planted before backfill", 10, dao.getAllTemplatesOnce().size)

        // Phase 1: backfiller heals pulled rows by name.
        backfiller.runBackfillIfNeeded()

        val postBackfill = dao.getAllTemplatesOnce()
        assertEquals("no rows deleted yet", 10, postBackfill.size)
        val healedPulled = pulledIds.map { id -> dao.getTemplateById(id)!! }
        healedPulled.forEach { row ->
            assertNotNull("pulled row must have template_key set", row.templateKey)
            assertTrue("pulled row must be flagged built-in", row.isBuiltIn)
            assertTrue(
                "cloud_id preserved on the healed pulled row",
                row.cloudId!!.startsWith("firestore-doc-for-")
            )
        }
        assertFalse(
            "backfiller must reset reconciler flag so it re-runs this cycle",
            reconciledFlagHeld
        )
        coVerify(exactly = BUILT_IN_TEMPLATES.size) { syncTracker.trackUpdate(any(), "task_template") }

        // Phase 2: reconciler fires (flag was reset by backfiller → runs).
        reconciler.reconcileAfterSyncIfNeeded()

        val final = dao.getAllTemplatesOnce()
        assertEquals(
            "reconciler collapses each templateKey group to one survivor",
            BUILT_IN_TEMPLATES.size,
            final.size
        )
        // The keeper is chosen by lex-smallest cloud_id (non-null wins), so the
        // pulled row with the cloud_id should survive, not the reseeded null-cloud row.
        final.forEach { keeper ->
            assertNotNull("survivor must carry the pulled cloud_id", keeper.cloudId)
            assertTrue(
                "survivor's cloud_id must be the firestore-doc one",
                keeper.cloudId!!.startsWith("firestore-doc-for-")
            )
            assertNotNull("survivor must have template_key from backfill", keeper.templateKey)
            assertTrue(keeper.isBuiltIn)
        }
        // Reseeded (null-cloud) rows must have been deleted as losers.
        reseededIds.forEach { id ->
            assertNull(
                "reseeded null-cloud row should be deleted as loser",
                dao.getTemplateById(id)
            )
        }
        assertTrue(
            "reconciler must flip its flag back to true on completion",
            reconciledFlagHeld
        )
    }

    @Test
    fun backfiller_then_reconciler_leavesUserContentAlone() = runTest {
        val dao = database.taskTemplateDao()

        // Reseeded built-in.
        val reseededId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "builtin_weekly_review",
                isBuiltIn = true
            )
        )
        // Pulled built-in twin.
        val pulledId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = null,
                isBuiltIn = false,
                cloudId = "pulled-review-cloud-id"
            )
        )
        // User-created row with a name that happens to match — guard must prevent healing.
        val userId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = null,
                isBuiltIn = false,
                usageCount = 7,
                lastUsedAt = 123456789L
            )
        )
        // Completely unrelated user content.
        val unrelatedUserId = dao.insertTemplate(
            TaskTemplateEntity(
                name = "My Unique Template",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()
        reconciler.reconcileAfterSyncIfNeeded()

        // Post-flow: exactly 3 rows survive.
        //   1. The merged built-in (pulled wins as keeper with cloud_id)
        //   2. The usage_count>0 row (backfill skipped it; reconciler ignored because templateKey null)
        //   3. The unrelated user template (no match anywhere)
        val final = dao.getAllTemplatesOnce()
        assertEquals("3 rows survive", 3, final.size)

        val survivingBuiltIn = final.single { it.isBuiltIn && it.templateKey == "builtin_weekly_review" }
        assertEquals("pulled cloud_id kept", "pulled-review-cloud-id", survivingBuiltIn.cloudId)
        assertNull("reseeded built-in deleted", dao.getTemplateById(reseededId))
        assertNotNull("user row preserved verbatim", dao.getTemplateById(userId))
        assertNotNull("unrelated user row preserved", dao.getTemplateById(unrelatedUserId))
        assertEquals(
            "user row shape untouched",
            7,
            dao.getTemplateById(userId)!!.usageCount
        )
    }

    @Test
    fun backfiller_noHealNeeded_reconcilerFlagNotReset() = runTest {
        val dao = database.taskTemplateDao()
        // Clean starting state: one already-healed built-in. Backfiller has nothing to do.
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Weekly Review",
                templateKey = "builtin_weekly_review",
                isBuiltIn = true,
                cloudId = "already-synced-id"
            )
        )
        // Pretend reconciler already completed previously.
        reconciledFlagHeld = true

        backfiller.runBackfillIfNeeded()

        assertTrue(
            "backfiller must NOT reset reconciler flag when nothing was healed — " +
                "no reason to re-run a passing reconciler",
            reconciledFlagHeld
        )
        assertTrue(
            "backfiller's own done-flag flipped regardless",
            backfillFlagHeld
        )
    }

    @Test
    fun backfiller_secondRun_isNoOp() = runTest {
        val dao = database.taskTemplateDao()
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Meeting Prep",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()
        val afterFirstRun = dao.getAllTemplatesOnce().single()
        assertEquals("builtin_meeting_prep", afterFirstRun.templateKey)

        // Plant a SECOND orphaned row after the flag was set. Backfiller should
        // NOT heal it because its done-flag is true.
        dao.insertTemplate(
            TaskTemplateEntity(
                name = "Grocery Run",
                templateKey = null,
                isBuiltIn = false
            )
        )

        backfiller.runBackfillIfNeeded()
        val groceryRow = dao.getAllTemplatesOnce().single { it.name == "Grocery Run" }
        assertNull(
            "second run must be gated by done-flag; new orphans NOT healed",
            groceryRow.templateKey
        )
    }

    companion object {
        // Subset of TemplateSeeder.BUILT_IN_TEMPLATES used for this integration
        // test. Kept inline to keep the test self-contained — if the seeder list
        // ever drifts from these names, the test will still pass against the
        // subset it declares.
        private val BUILT_IN_TEMPLATES: List<Pair<String, String>> = listOf(
            "Weekly Review" to "builtin_weekly_review",
            "Meeting Prep" to "builtin_meeting_prep",
            "Grocery Run" to "builtin_grocery_run",
            "School Daily" to "builtin_school_daily",
            "Leisure Time" to "builtin_leisure_time"
        )
    }
}
