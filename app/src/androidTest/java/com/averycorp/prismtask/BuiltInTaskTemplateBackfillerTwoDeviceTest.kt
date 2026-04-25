package com.averycorp.prismtask

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.BuiltInTaskTemplateBackfiller
import com.averycorp.prismtask.data.remote.BuiltInTaskTemplateReconciler
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two-device convergence test for the SPEC_BUILT_IN_TASK_TEMPLATE_RECONCILER
 * `TaskTemplateRecoveryTest` scenario (spec §7). Simulates two app instances
 * that both reseeded the built-in templates AND pulled the 8 Firestore docs
 * created before `template_key` + `is_built_in` existed. Walks the full
 * chain: device A backfills + pushes updated docs, device B receives the
 * updates via a simulated pull, then device B's reconciler correctly
 * collapses its locally-visible dupes.
 *
 * The Firestore "doc store" is modeled as a
 * `MutableMap<String, MutableMap<String, Any?>>` (cloud_id → field map)
 * shared by both device harnesses. Writes mutate the map; pulls read the
 * map and apply `mapToTaskTemplate`-style updates to the receiving
 * device's Room DB. No Firestore SDK interaction.
 *
 * Why this shape: CI only runs `testDebugUnitTest`, so emulator-backed
 * instrumented tests would not be verified in CI today. Two in-process
 * Room DBs + shared simulated state exercise the cross-device
 * convergence narrative without that infrastructure debt.
 */
@RunWith(AndroidJUnit4::class)
class BuiltInTaskTemplateBackfillerTwoDeviceTest {
    private lateinit var deviceA: Device
    private lateinit var deviceB: Device
    private lateinit var firestore: SimulatedFirestore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        firestore = SimulatedFirestore()
        deviceA = Device.build(context, firestore)
        deviceB = Device.build(context, firestore)
    }

    @After
    fun tearDown() {
        deviceA.close()
        deviceB.close()
    }

    /**
     * Canonical scenario:
     *  1. Firestore has 5 task_template docs created pre-`template_key`
     *     column — each doc has `name`/`description`/etc. but NO
     *     `templateKey` or `isBuiltIn` fields.
     *  2. Both devices reseeded the 5 current built-ins locally (name +
     *     templateKey + is_built_in=1, cloud_id=null).
     *  3. Both devices pulled Firestore's 5 docs → 5 extra rows per
     *     device (cloud_id set, templateKey=null, is_built_in=false).
     *  4. Pre-flow: each device has 10 rows (5 reseeded + 5 pulled).
     *  5. Device A runs backfiller → 5 pulled rows get templateKey +
     *     is_built_in healed, and A enqueues 5 pending updates.
     *  6. Simulate A's reactive push → Firestore docs updated with
     *     the missing fields.
     *  7. Device B performs a simulated pull → receives A's updates,
     *     so B's previously-null-templateKey rows now have them set.
     *  8. Device B's reconciler fires → sees 10 rows with is_built_in=1
     *     and 5 templateKey groups of size 2, collapses each group to
     *     one keeper (lex-smallest cloud_id wins), leaving B with 5
     *     merged rows.
     */
    @Test
    fun backfillOnA_pushToFirestore_pullOnB_reconcileOnB() = runTest {
        // ── Phase 1: pre-existing Firestore docs lack template_key / is_built_in ──
        val pulledDocs = listOf(
            cloudDoc("fs-weekly-review", name = "Weekly Review"),
            cloudDoc("fs-meeting-prep", name = "Meeting Prep"),
            cloudDoc("fs-grocery-run", name = "Grocery Run"),
            cloudDoc("fs-school-daily", name = "School Daily"),
            cloudDoc("fs-leisure-time", name = "Leisure Time")
        )
        pulledDocs.forEach { (cid, data) -> firestore.put("task_templates", cid, data) }

        // ── Phase 2 + 3: both devices reseeded + pulled ──
        deviceA.seedReseededBuiltIns(CURRENT_BUILT_INS)
        deviceA.seedPulledLegacyRows(pulledDocs)
        deviceB.seedReseededBuiltIns(CURRENT_BUILT_INS)
        deviceB.seedPulledLegacyRows(pulledDocs)

        assertEquals(
            "Each device has 10 rows pre-backfill",
            10,
            deviceA.allTemplates().size
        )
        assertEquals(10, deviceB.allTemplates().size)

        // ── Phase 5: device A backfills ──
        deviceA.runBackfiller()

        val aHealed = deviceA.allTemplates().filter { it.cloudId?.startsWith("fs-") == true }
        assertEquals(5, aHealed.size)
        aHealed.forEach { row ->
            assertNotNull("pulled row on A healed with template_key", row.templateKey)
            assertTrue("pulled row on A flagged built-in", row.isBuiltIn)
        }
        assertEquals(
            "A enqueued 5 pending updates for pushed templates",
            5,
            deviceA.pendingActions("task_template").size
        )

        // ── Phase 6: simulate A's reactive push to Firestore ──
        deviceA.simulatePushForPending()
        // Firestore docs now include templateKey + isBuiltIn.
        pulledDocs.map { it.first }.forEach { cid ->
            val doc = firestore.doc("task_templates", cid)!!
            assertNotNull("doc $cid now has templateKey", doc["templateKey"])
            assertEquals(true, doc["isBuiltIn"])
        }

        // ── Phase 7: device B performs a simulated pull of the 5 updated docs ──
        deviceB.simulatePullForCollection("task_templates")

        val bPulledRows = deviceB.allTemplates()
            .filter { it.cloudId?.startsWith("fs-") == true }
        assertEquals(5, bPulledRows.size)
        bPulledRows.forEach { row ->
            assertNotNull("B's pulled row now has template_key after pull", row.templateKey)
            assertTrue("B's pulled row now is_built_in after pull", row.isBuiltIn)
        }

        // ── Phase 8: device B's reconciler collapses dupes ──
        deviceB.runReconciler()

        val bFinal = deviceB.allTemplates()
        assertEquals(
            "Device B converges to 5 merged rows (one per templateKey)",
            CURRENT_BUILT_INS.size,
            bFinal.size
        )
        bFinal.forEach { keeper ->
            assertNotNull("survivor has template_key", keeper.templateKey)
            assertTrue("survivor is_built_in", keeper.isBuiltIn)
            assertNotNull("survivor has cloud_id (pulled row won the merge)", keeper.cloudId)
            assertTrue(
                "survivor's cloud_id is the Firestore doc id, not null",
                keeper.cloudId!!.startsWith("fs-")
            )
        }
    }

    /**
     * After A backfills + pushes but BEFORE B pulls, Firestore is in a
     * state where B's local rows disagree with the cloud (B has null
     * templateKey locally; cloud has real values). This tests the
     * ordering: reconciler on B BEFORE pull must not merge the dupes
     * (because B's pulled rows still look like non-built-ins). The spec
     * protects against this by gating the reconciler on the
     * `isBuiltInTaskTemplatesReconciled` flag; in the normal flow the
     * backfiller on B would reset the flag AFTER its own backfill. Here
     * B hasn't run the backfiller yet, so the reconciler short-circuits
     * (since its default behavior is "filter to is_built_in=1" which
     * leaves B's pulled rows out of scope). Verifies the early-exit.
     */
    @Test
    fun reconcilerBeforePull_doesNotPrematurelyMergeAcrossDevices() = runTest {
        val pulledDocs = listOf(
            cloudDoc("fs-weekly-review", name = "Weekly Review")
        )
        pulledDocs.forEach { (cid, data) -> firestore.put("task_templates", cid, data) }

        deviceA.seedReseededBuiltIns(listOf("Weekly Review" to "builtin_weekly_review"))
        deviceA.seedPulledLegacyRows(pulledDocs)
        deviceB.seedReseededBuiltIns(listOf("Weekly Review" to "builtin_weekly_review"))
        deviceB.seedPulledLegacyRows(pulledDocs)

        // Backfiller on A; don't push yet.
        deviceA.runBackfiller()

        // Reconciler on B — B has NOT pulled A's push, so B's pulled row
        // is still is_built_in=false / template_key=null.
        deviceB.runReconciler()

        assertEquals(
            "B still has 2 rows: reseeded + pulled-but-not-healed — reconciler " +
                "didn't have a group of size>1 in is_built_in=1 scope",
            2,
            deviceB.allTemplates().size
        )
    }

    // ───────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Builds a (cloudId, field map) pair representing a pre-template_key
     * Firestore doc: has `name` + bare mapper-readable fields, missing
     * `templateKey` and `isBuiltIn`.
     */
    private fun cloudDoc(cloudId: String, name: String): Pair<String, MutableMap<String, Any?>> =
        cloudId to mutableMapOf<String, Any?>(
            "name" to name,
            "createdAt" to 0L,
            "updatedAt" to 0L
        )

    private class Device private constructor(
        private val database: PrismTaskDatabase,
        private val backfiller: BuiltInTaskTemplateBackfiller,
        private val reconciler: BuiltInTaskTemplateReconciler,
        private val firestore: SimulatedFirestore
    ) {
        suspend fun seedReseededBuiltIns(builtIns: List<Pair<String, String>>) {
            val dao = database.taskTemplateDao()
            builtIns.forEach { (name, key) ->
                dao.insertTemplate(
                    TaskTemplateEntity(
                        name = name,
                        templateKey = key,
                        isBuiltIn = true
                    )
                )
            }
        }

        suspend fun seedPulledLegacyRows(
            docs: List<Pair<String, MutableMap<String, Any?>>>
        ) {
            val dao = database.taskTemplateDao()
            val metaDao = database.syncMetadataDao()
            docs.forEach { (cid, data) ->
                val localId = dao.insertTemplate(
                    TaskTemplateEntity(
                        name = data["name"] as String,
                        templateKey = null,
                        isBuiltIn = false,
                        cloudId = cid
                    )
                )
                metaDao.upsert(
                    SyncMetadataEntity(
                        localId = localId,
                        entityType = "task_template",
                        cloudId = cid,
                        lastSyncedAt = 1L
                    )
                )
            }
        }

        suspend fun runBackfiller() = backfiller.runBackfillIfNeeded()

        suspend fun runReconciler() = reconciler.reconcileAfterSyncIfNeeded()

        suspend fun allTemplates(): List<TaskTemplateEntity> =
            database.taskTemplateDao().getAllTemplatesOnce()

        suspend fun pendingActions(entityType: String): List<SyncMetadataEntity> =
            database.syncMetadataDao()
                .getPendingActions()
                .filter { it.entityType == entityType }

        /**
         * Simulates the reactive-push observer: for each pending
         * task_template update, write the local row's current shape to
         * the simulated Firestore doc at the matching cloud_id and
         * clear the pending action.
         */
        suspend fun simulatePushForPending() {
            val dao = database.taskTemplateDao()
            val metaDao = database.syncMetadataDao()
            val pending = metaDao.getPendingActions()
                .filter { it.entityType == "task_template" }
            for (meta in pending) {
                val row = dao.getTemplateById(meta.localId) ?: continue
                val doc = firestore.doc("task_templates", meta.cloudId)
                    ?: mutableMapOf<String, Any?>().also { firestore.put("task_templates", meta.cloudId, it) }
                doc["templateKey"] = row.templateKey
                doc["isBuiltIn"] = row.isBuiltIn
                doc["name"] = row.name
                doc["updatedAt"] = row.updatedAt
                metaDao.clearPendingAction(meta.localId, meta.entityType)
            }
        }

        /**
         * Simulates `SyncService.pullRemoteChanges`'s task_templates
         * handler: for each doc currently in the simulated Firestore
         * collection, look up the matching local row by cloud_id and
         * apply the Firestore-side fields via `updateTemplate`.
         * Mirrors `SyncMapper.mapToTaskTemplate` narrowly — only the
         * fields the backfiller writes are exercised.
         */
        suspend fun simulatePullForCollection(name: String) {
            val dao = database.taskTemplateDao()
            val metaDao = database.syncMetadataDao()
            val docs = firestore.collectionDocs(name)
            for ((cid, data) in docs) {
                val localId = metaDao.getLocalId(cid, "task_template") ?: continue
                val row = dao.getTemplateById(localId) ?: continue
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (remoteUpdatedAt > row.updatedAt) {
                    dao.updateTemplate(
                        row.copy(
                            templateKey = data["templateKey"] as? String,
                            isBuiltIn = data["isBuiltIn"] as? Boolean ?: row.isBuiltIn,
                            updatedAt = remoteUpdatedAt
                        )
                    )
                }
            }
        }

        fun close() {
            database.close()
        }

        companion object {
            /**
             * Mutable pair of flags the backfiller + reconciler read/write
             * via the mocked [BuiltInSyncPreferences]. Captured in the
             * factory below so mockk writes visibly affect subsequent reads.
             */
            private class FlagHolder {
                var backfillDone = false
                var reconciled = false
            }

            fun build(
                context: android.content.Context,
                firestore: SimulatedFirestore
            ): Device {
                val db = Room
                    .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                val flags = FlagHolder()
                val prefs = mockk<BuiltInSyncPreferences>(relaxed = false)
                coEvery { prefs.isTaskTemplateBackfillDone() } answers { flags.backfillDone }
                val backfillSlot = slot<Boolean>()
                coEvery { prefs.setTaskTemplateBackfillDone(capture(backfillSlot)) } answers {
                    flags.backfillDone = backfillSlot.captured
                }
                coEvery { prefs.isBuiltInTaskTemplatesReconciled() } answers { flags.reconciled }
                val reconciledSlot = slot<Boolean>()
                coEvery { prefs.setBuiltInTaskTemplatesReconciled(capture(reconciledSlot)) } answers {
                    flags.reconciled = reconciledSlot.captured
                }
                coJustRun { prefs.setNewEntitiesBackfillDone(any()) }
                val logger = mockk<PrismSyncLogger>(relaxed = true)
                // Use a real SyncTracker with a stub AuthManager returning a
                // non-null userId so trackUpdate() actually writes to
                // syncMetadataDao. A mock SyncTracker silently swallowed the
                // trackUpdate calls, which is why pendingActions() returned
                // 0 even after the backfiller healed 5 rows.
                val authManager = mockk<AuthManager>()
                every { authManager.userId } returns "test-user"
                val syncTracker = SyncTracker(authManager, db.syncMetadataDao(), logger)
                val backfiller = BuiltInTaskTemplateBackfiller(
                    taskTemplateDao = db.taskTemplateDao(),
                    syncTracker = syncTracker,
                    builtInSyncPreferences = prefs,
                    logger = logger
                )
                val reconciler = BuiltInTaskTemplateReconciler(
                    taskTemplateDao = db.taskTemplateDao(),
                    syncMetadataDao = db.syncMetadataDao(),
                    builtInSyncPreferences = prefs,
                    logger = logger
                )
                return Device(db, backfiller, reconciler, firestore)
            }
        }
    }

    /**
     * In-memory stand-in for Firestore's doc storage. Unlike the healer's
     * simpler `Set<String>` model, the template scenario needs actual doc
     * field maps because the pull path reads `templateKey` and `isBuiltIn`
     * off the doc body.
     */
    private class SimulatedFirestore {
        private val docs = mutableMapOf<String, MutableMap<String, MutableMap<String, Any?>>>()

        fun put(collection: String, docId: String, data: MutableMap<String, Any?>) {
            docs.getOrPut(collection) { mutableMapOf() }[docId] = data
        }

        fun doc(collection: String, docId: String): MutableMap<String, Any?>? =
            docs[collection]?.get(docId)

        fun collectionDocs(collection: String): Map<String, MutableMap<String, Any?>> =
            docs[collection] ?: emptyMap()
    }

    companion object {
        private val CURRENT_BUILT_INS: List<Pair<String, String>> = listOf(
            "Weekly Review" to "builtin_weekly_review",
            "Meeting Prep" to "builtin_meeting_prep",
            "Grocery Run" to "builtin_grocery_run",
            "School Daily" to "builtin_school_daily",
            "Leisure Time" to "builtin_leisure_time"
        )
    }
}
