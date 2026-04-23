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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-Firestore counterpart to
 * [BuiltInTaskTemplateBackfillerTwoDeviceTest]. Exercises the full
 * SPEC_BUILT_IN_TASK_TEMPLATE_RECONCILER cross-device heal + reconcile
 * chain against the live Firebase Emulator Suite. Each "pull" on device
 * B reads the real Firestore doc that device A's simulated push wrote,
 * so the `templateKey` + `isBuiltIn` field transfer is verified against
 * the actual SDK serialization behavior instead of an in-process map.
 *
 * Gated by `Assume.assumeTrue(BuildConfig.USE_FIREBASE_EMULATOR)`.
 *
 * Each test uses a unique userId namespace
 * (`emulator-template-{ts}`) to isolate state within a single emulator
 * lifetime.
 */
@RunWith(AndroidJUnit4::class)
class BuiltInTaskTemplateBackfillerEmulatorTest {
    private lateinit var deviceA: Device
    private lateinit var deviceB: Device
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String

    @Before
    fun setUp() {
        assumeTrue(
            "Requires USE_FIREBASE_EMULATOR=true — skipped on default debug builds.",
            BuildConfig.USE_FIREBASE_EMULATOR
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirebaseApp.initializeApp(context)
        try {
            FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings
                    .Builder()
                    .setPersistenceEnabled(false)
                    .build()
        } catch (_: IllegalStateException) {
            // Already routed.
        }
        try {
            FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_PORT)
        } catch (_: IllegalStateException) {
            // Already routed.
        }
        // firestore.rules requires request.auth != null; sign in to the Auth
        // emulator so the real-SDK writes below aren't rejected with
        // PERMISSION_DENIED. Anonymous sign-in is always enabled on the
        // emulator and produces a fresh uid per test run.
        runBlocking {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        }
        firestore = FirebaseFirestore.getInstance()
        userId = "emulator-template-${System.currentTimeMillis()}"
        deviceA = Device.build(context, userId, firestore)
        deviceB = Device.build(context, userId, firestore)
    }

    @After
    fun tearDown() {
        deviceA.close()
        deviceB.close()
    }

    /**
     * Full cross-device flow against real Firestore:
     *  1. Firestore has 5 legacy docs (`name` set, `templateKey` +
     *     `isBuiltIn` missing — the shape that pre-dates the columns).
     *  2. Both devices reseeded the 5 current built-ins AND pulled the
     *     5 legacy rows → 10 rows per device.
     *  3. Device A runs backfiller → heals 5 rows + enqueues updates.
     *  4. A's simulated push writes `templateKey` + `isBuiltIn` to
     *     Firestore docs (via the real SDK).
     *  5. Device B's simulated pull reads those fields from Firestore
     *     and applies them to its local rows.
     *  6. Device B's reconciler collapses 10 → 5 keepers, with the
     *     Firestore-sourced cloud_ids surviving.
     */
    @Test
    fun backfillOnA_pushToRealFirestore_pullOnB_reconcileOnB() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val legacyDocs = BUILT_INS.map { (name, _) -> "fs-$name".replace(" ", "-").lowercase() to name }

            // ── Phase 1: seed legacy Firestore docs (pre-templateKey era) ──
            for ((cid, name) in legacyDocs) {
                userCollection("task_templates").document(cid).set(
                    mapOf(
                        "name" to name,
                        "createdAt" to 0L,
                        "updatedAt" to 0L
                    )
                ).await()
            }

            // ── Phase 2+3: both devices reseeded + pulled ──
            deviceA.seedReseededBuiltIns(BUILT_INS)
            deviceA.seedPulledLegacyRows(legacyDocs)
            deviceB.seedReseededBuiltIns(BUILT_INS)
            deviceB.seedPulledLegacyRows(legacyDocs)

            assertEquals(10, deviceA.allTemplates().size)
            assertEquals(10, deviceB.allTemplates().size)

            // ── Phase 5: A backfills ──
            deviceA.runBackfiller()

            val aHealed = deviceA.allTemplates().filter { it.cloudId?.startsWith("fs-") == true }
            assertEquals(BUILT_INS.size, aHealed.size)
            aHealed.forEach { row ->
                assertNotNull("A's pulled row healed with templateKey", row.templateKey)
                assertTrue("A's pulled row flagged built-in", row.isBuiltIn)
            }
            assertEquals(BUILT_INS.size, deviceA.pendingActions("task_template").size)

            // ── Phase 6: simulated push via real Firestore SDK ──
            deviceA.simulatePushForPending()
            for ((cid, _) in legacyDocs) {
                val doc = userCollection("task_templates").document(cid).get().await()
                assertNotNull("doc $cid now has templateKey in real Firestore", doc.getString("templateKey"))
                assertEquals(true, doc.getBoolean("isBuiltIn"))
            }

            // ── Phase 7: simulated pull via real Firestore SDK ──
            deviceB.simulatePullForCollection("task_templates")
            val bPulledRows = deviceB.allTemplates().filter { it.cloudId?.startsWith("fs-") == true }
            assertEquals(BUILT_INS.size, bPulledRows.size)
            bPulledRows.forEach { row ->
                assertNotNull("B's pulled row has templateKey after real-Firestore pull", row.templateKey)
                assertTrue("B's pulled row is_built_in after real-Firestore pull", row.isBuiltIn)
            }

            // ── Phase 8: B's reconciler ──
            deviceB.runReconciler()
            val bFinal = deviceB.allTemplates()
            assertEquals(
                "B converges to one merged row per templateKey",
                BUILT_INS.size,
                bFinal.size
            )
            bFinal.forEach { keeper ->
                assertNotNull(keeper.templateKey)
                assertTrue(keeper.isBuiltIn)
                assertNotNull(keeper.cloudId)
                assertTrue("pulled cloud_id survived the merge", keeper.cloudId!!.startsWith("fs-"))
            }
        }
    }

    @Test
    fun reconcilerBeforePull_doesNotPrematurelyMerge() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val cid = "fs-weekly-review"
            userCollection("task_templates").document(cid).set(
                mapOf("name" to "Weekly Review", "createdAt" to 0L, "updatedAt" to 0L)
            ).await()

            val singleBuiltIn = listOf("Weekly Review" to "builtin_weekly_review")
            deviceA.seedReseededBuiltIns(singleBuiltIn)
            deviceA.seedPulledLegacyRows(listOf(cid to "Weekly Review"))
            deviceB.seedReseededBuiltIns(singleBuiltIn)
            deviceB.seedPulledLegacyRows(listOf(cid to "Weekly Review"))

            deviceA.runBackfiller() // don't push yet
            deviceB.runReconciler()

            assertEquals(
                "B still has 2 rows — reconciler's is_built_in=1 filter skipped " +
                    "the pulled-but-not-healed row before B's pull",
                2,
                deviceB.allTemplates().size
            )
        }
    }

    private fun userCollection(name: String) =
        firestore.collection("users").document(userId).collection(name)

    private class Device private constructor(
        private val database: PrismTaskDatabase,
        private val backfiller: BuiltInTaskTemplateBackfiller,
        private val reconciler: BuiltInTaskTemplateReconciler,
        private val userId: String,
        private val firestore: FirebaseFirestore
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

        suspend fun seedPulledLegacyRows(docs: List<Pair<String, String>>) {
            val dao = database.taskTemplateDao()
            val metaDao = database.syncMetadataDao()
            docs.forEach { (cid, name) ->
                val localId = dao.insertTemplate(
                    TaskTemplateEntity(
                        name = name,
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

        suspend fun simulatePushForPending() {
            val dao = database.taskTemplateDao()
            val metaDao = database.syncMetadataDao()
            val pending = metaDao.getPendingActions()
                .filter { it.entityType == "task_template" }
            for (meta in pending) {
                val row = dao.getTemplateById(meta.localId) ?: continue
                firestore
                    .collection("users").document(userId)
                    .collection("task_templates").document(meta.cloudId)
                    .set(
                        mapOf(
                            "name" to row.name,
                            "templateKey" to row.templateKey,
                            "isBuiltIn" to row.isBuiltIn,
                            "updatedAt" to row.updatedAt
                        )
                    )
                    .await()
                metaDao.clearPendingAction(meta.localId, meta.entityType)
            }
        }

        /**
         * Reads real Firestore docs and applies fields to local rows
         * with matching cloud_ids. Mirrors the subset of
         * [com.averycorp.prismtask.data.remote.SyncService.pullRemoteChanges]
         * that this test cares about — templateKey + isBuiltIn + updatedAt
         * transfer.
         */
        suspend fun simulatePullForCollection(name: String) {
            val dao = database.taskTemplateDao()
            val metaDao = database.syncMetadataDao()
            val snapshot = firestore
                .collection("users").document(userId)
                .collection(name)
                .get().await()
            for (doc in snapshot.documents) {
                val localId = metaDao.getLocalId(doc.id, "task_template") ?: continue
                val row = dao.getTemplateById(localId) ?: continue
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                if (remoteUpdatedAt > row.updatedAt) {
                    dao.updateTemplate(
                        row.copy(
                            templateKey = doc.getString("templateKey"),
                            isBuiltIn = doc.getBoolean("isBuiltIn") ?: row.isBuiltIn,
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
            private class FlagHolder {
                var backfillDone = false
                var reconciled = false
            }

            fun build(
                context: android.content.Context,
                userId: String,
                firestore: FirebaseFirestore
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
                val syncTracker = mockk<SyncTracker>(relaxed = true)
                val logger = mockk<PrismSyncLogger>(relaxed = true)
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
                return Device(db, backfiller, reconciler, userId, firestore)
            }
        }
    }

    companion object {
        private const val EMULATOR_HOST = "10.0.2.2"
        private const val FIRESTORE_PORT = 8080
        private const val AUTH_PORT = 9099
        private const val TEST_TIMEOUT_MS = 60_000L

        private val BUILT_INS: List<Pair<String, String>> = listOf(
            "Weekly Review" to "builtin_weekly_review",
            "Meeting Prep" to "builtin_meeting_prep",
            "Grocery Run" to "builtin_grocery_run",
            "School Daily" to "builtin_school_daily",
            "Leisure Time" to "builtin_leisure_time"
        )
    }
}
