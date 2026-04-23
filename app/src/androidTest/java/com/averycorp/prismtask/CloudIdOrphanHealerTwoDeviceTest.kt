package com.averycorp.prismtask

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.CloudIdOrphanHealer
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two-device convergence test for [CloudIdOrphanHealer], covering the
 * `SelfCareStepsOutOfBandRecoveryTest` scenario called out in
 * `docs/SPEC_SELF_CARE_STEPS_SYNC_PIPELINE.md` §7. Simulates two independent
 * app instances (each with its own isolated Room DB) talking to a shared
 * in-memory "fake Firestore" store represented as a `MutableSet<String>` of
 * doc IDs per collection. The `SimulatedFirestore` type handles push/pull
 * round-trips the way the real SDK would — writes append to the set, pulls
 * observe the current set — so healer outputs can be routed between devices
 * deterministically without mocking the Firestore SDK.
 *
 * Why this shape instead of a real Firebase emulator instrumented test:
 *  - Android CI runs `testDebugUnitTest` only; it does NOT run
 *    `connectedDebugAndroidTest`, so an emulator-backed instrumented test
 *    would never be exercised by CI (see `.github/workflows/android-ci.yml`).
 *  - Adding a second CI job plus a Firebase Emulator Suite boot step is
 *    out of scope for this PR; tracked as a follow-up.
 *  - Two-DB + shared state faithfully reproduces the inter-device
 *    convergence narrative (a push from one instance becoming visible to
 *    the other's pull), which is the specific behavior the spec tests.
 */
@RunWith(AndroidJUnit4::class)
class CloudIdOrphanHealerTwoDeviceTest {
    private lateinit var deviceA: Device
    private lateinit var deviceB: Device
    private lateinit var firestore: SimulatedFirestore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        firestore = SimulatedFirestore()
        deviceA = Device.build(context, label = "A", firestore = firestore)
        deviceB = Device.build(context, label = "B", firestore = firestore)
    }

    @After
    fun tearDown() {
        deviceA.close()
        deviceB.close()
    }

    /**
     * Full SelfCareStepsOutOfBandRecoveryTest scenario:
     *  1. Both devices + Firestore start with the same 5 self_care_steps.
     *  2. Firestore subcollection is wiped out of band.
     *  3. Device A runs healer → enqueues 5 pending updates.
     *  4. Simulate device A's reactive-push to Firestore → 5 docs re-created
     *     at the same cloud_ids they had before the wipe.
     *  5. Device B runs healer → sees docs are present again → no-op.
     *  6. Both devices' sync_metadata agree on the 5 cloud_ids.
     */
    @Test
    fun outOfBandWipe_deviceAHeals_deviceBNoOpsWhenDocsReappear() = runTest {
        // ── Phase 1: steady state ──
        val stepIds = (1..5).map { i -> "firestore-doc-$i" }
        stepIds.forEach { cid -> firestore.put("self_care_steps", cid) }

        deviceA.seedStepsWithCloudIds(stepIds)
        deviceB.seedStepsWithCloudIds(stepIds)
        assertEquals(5, firestore.collection("self_care_steps").size)
        assertEquals(5, deviceA.allLocalCloudIds("self_care_steps").size)
        assertEquals(5, deviceB.allLocalCloudIds("self_care_steps").size)

        // ── Phase 2: out-of-band wipe ──
        firestore.wipe("self_care_steps")
        assertEquals(
            "Firestore collection wiped; local rows unchanged",
            0,
            firestore.collection("self_care_steps").size
        )
        assertEquals(5, deviceA.allLocalCloudIds("self_care_steps").size)
        assertEquals(5, deviceB.allLocalCloudIds("self_care_steps").size)

        // ── Phase 3: device A's healer enqueues pending updates ──
        deviceA.runHealer()
        assertEquals(
            "Device A queued 5 orphan pushes",
            5,
            deviceA.pendingActions("self_care_step").size
        )
        deviceA.pendingActions("self_care_step").forEach { meta ->
            assertEquals("update", meta.pendingAction)
            assertTrue(
                "Pending push reuses the original cloud_id — other devices' references stay stable",
                meta.cloudId in stepIds
            )
        }

        // ── Phase 4: simulate device A reactive push landing in Firestore ──
        deviceA.simulatePushForPending("self_care_step")
        assertEquals(
            "All 5 docs re-created at the same cloud_ids",
            stepIds.toSet(),
            firestore.collection("self_care_steps")
        )

        // ── Phase 5: device B's healer now sees docs present again ──
        deviceB.runHealer()
        assertEquals(
            "Device B sees docs in Firestore → no orphan recovery needed",
            0,
            deviceB.pendingActions("self_care_step").size
        )

        // ── Phase 6: convergence check ──
        val aCids = deviceA.syncedCloudIds("self_care_step").toSet()
        val bCids = deviceB.syncedCloudIds("self_care_step").toSet()
        val firestoreCids = firestore.collection("self_care_steps")
        assertEquals("Device A sync_metadata matches Firestore", firestoreCids, aCids)
        assertEquals("Device B sync_metadata matches Firestore", firestoreCids, bCids)
    }

    /**
     * Divergent orphan sets — one device restored from backup with 3 rows
     * unique to it, the other has 2 rows unique to it. Firestore is empty.
     * Both devices heal independently; their pushes land in Firestore; a
     * subsequent pull would merge the two sets. Here we just verify the
     * push side converges (pull path is out of scope — covered by
     * `pullCollection`'s stepId+routineType dedup at SyncService.kt:1151).
     */
    @Test
    fun divergentOrphans_eachDevicePushesItsOwnRows() = runTest {
        val aCids = listOf("a-1", "a-2", "a-3")
        val bCids = listOf("b-1", "b-2")
        deviceA.seedStepsWithCloudIds(aCids)
        deviceB.seedStepsWithCloudIds(bCids)
        // Firestore starts empty — all 5 local rows are orphans.

        deviceA.runHealer()
        deviceB.runHealer()
        deviceA.simulatePushForPending("self_care_step")
        deviceB.simulatePushForPending("self_care_step")

        val firestoreCids = firestore.collection("self_care_steps")
        assertEquals(
            "Firestore now has the union of both devices' cloud_ids",
            (aCids + bCids).toSet(),
            firestoreCids
        )
    }

    /**
     * Healer must not re-enqueue a row that the other device ALREADY
     * pushed between Firestore wipe and this device's sync cycle.
     * Device B races device A's push: by the time B's healer runs, A's 5
     * docs are already back in Firestore, so B's healer is a no-op —
     * proving the healer is monotonic across device-interleave orderings.
     */
    @Test
    fun deviceBHealerRunsAfterDeviceAPush_noDoubleEnqueue() = runTest {
        val stepIds = listOf("shared-1", "shared-2", "shared-3")
        stepIds.forEach { cid -> firestore.put("self_care_steps", cid) }
        deviceA.seedStepsWithCloudIds(stepIds)
        deviceB.seedStepsWithCloudIds(stepIds)

        firestore.wipe("self_care_steps")
        deviceA.runHealer()
        deviceA.simulatePushForPending("self_care_step") // A wins the race

        deviceB.runHealer() // B arrives late

        assertEquals(
            "Device B sees A's push result → no-op",
            0,
            deviceB.pendingActions("self_care_step").size
        )
    }

    /**
     * Mixed state — Firestore has docs 1-3 after a partial wipe; local
     * devices had docs 1-5. Healer correctly targets only docs 4-5.
     */
    @Test
    fun partialFirestoreWipe_healerOnlyTargetsMissingIds() = runTest {
        val allIds = listOf("cid-1", "cid-2", "cid-3", "cid-4", "cid-5")
        deviceA.seedStepsWithCloudIds(allIds)
        firestore.put("self_care_steps", "cid-1")
        firestore.put("self_care_steps", "cid-2")
        firestore.put("self_care_steps", "cid-3")
        // cid-4 and cid-5 are missing.

        deviceA.runHealer()

        val pending = deviceA.pendingActions("self_care_step")
        assertEquals(2, pending.size)
        assertEquals(
            setOf("cid-4", "cid-5"),
            pending.map { it.cloudId }.toSet()
        )
    }

    // ───────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Per-device test harness: one in-memory Room DB + one healer + a
     * reference to the shared simulated Firestore. `close()` tears the DB
     * down; each @Test gets a fresh pair.
     */
    private class Device private constructor(
        private val database: PrismTaskDatabase,
        private val healer: CloudIdOrphanHealer,
        private val firestore: SimulatedFirestore,
        val label: String
    ) {
        suspend fun seedStepsWithCloudIds(cloudIds: List<String>) {
            val stepDao = database.selfCareDao()
            val metaDao = database.syncMetadataDao()
            cloudIds.forEachIndexed { i, cid ->
                val localId = stepDao.insertStep(
                    SelfCareStepEntity(
                        stepId = "step-$label-$i",
                        routineType = "morning",
                        label = "Step $label/$i",
                        duration = "30s",
                        tier = "full",
                        note = "",
                        phase = "cleanse",
                        timeOfDay = "morning",
                        cloudId = cid
                    )
                )
                metaDao.upsert(
                    SyncMetadataEntity(
                        localId = localId,
                        entityType = "self_care_step",
                        cloudId = cid,
                        lastSyncedAt = 1L
                    )
                )
            }
        }

        suspend fun runHealer() {
            healer.healOrphans(fetcher = { collection ->
                firestore.collection(collection)
            })
        }

        suspend fun pendingActions(entityType: String): List<SyncMetadataEntity> =
            database.syncMetadataDao()
                .getPendingActions()
                .filter { it.entityType == entityType }

        suspend fun allLocalCloudIds(@Suppress("UNUSED_PARAMETER") table: String): Set<String> =
            database.selfCareDao()
                .getAllStepsOnce()
                .mapNotNull { it.cloudId }
                .toSet()

        suspend fun syncedCloudIds(entityType: String): List<String> {
            // There's no "get all by type" DAO method on SyncMetadataDao, so
            // iterate the local steps and look each one up. Sufficient because
            // the test harness controls what was seeded.
            val stepDao = database.selfCareDao()
            return stepDao.getAllStepsOnce().mapNotNull { step ->
                database.syncMetadataDao().getCloudId(step.id, entityType)
            }
        }

        /**
         * Simulates the reactive-push observer picking up this device's
         * pending-update queue and executing it against Firestore:
         * each doc is `set()` at its cloud_id (added to the simulated
         * collection) and its pending action is cleared.
         */
        suspend fun simulatePushForPending(entityType: String) {
            val metaDao = database.syncMetadataDao()
            val pending = metaDao.getPendingActions().filter { it.entityType == entityType }
            for (meta in pending) {
                firestore.put(collectionNameFor(entityType), meta.cloudId)
                metaDao.clearPendingAction(meta.localId, meta.entityType)
            }
        }

        fun close() {
            database.close()
        }

        companion object {
            fun build(
                context: android.content.Context,
                label: String,
                firestore: SimulatedFirestore
            ): Device {
                val db = Room
                    .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                val authManager = mockk<AuthManager>().apply {
                    every { userId } returns "test-user"
                }
                val logger = mockk<PrismSyncLogger>(relaxed = true)
                val healer = CloudIdOrphanHealer(
                    authManager = authManager,
                    syncMetadataDao = db.syncMetadataDao(),
                    selfCareDao = db.selfCareDao(),
                    schoolworkDao = db.schoolworkDao(),
                    leisureDao = db.leisureDao(),
                    taskDao = db.taskDao(),
                    projectDao = db.projectDao(),
                    tagDao = db.tagDao(),
                    habitDao = db.habitDao(),
                    habitCompletionDao = db.habitCompletionDao(),
                    habitLogDao = db.habitLogDao(),
                    taskCompletionDao = db.taskCompletionDao(),
                    taskTemplateDao = db.taskTemplateDao(),
                    milestoneDao = db.milestoneDao(),
                    notificationProfileDao = db.notificationProfileDao(),
                    customSoundDao = db.customSoundDao(),
                    savedFilterDao = db.savedFilterDao(),
                    nlpShortcutDao = db.nlpShortcutDao(),
                    habitTemplateDao = db.habitTemplateDao(),
                    projectTemplateDao = db.projectTemplateDao(),
                    boundaryRuleDao = db.boundaryRuleDao(),
                    checkInLogDao = db.checkInLogDao(),
                    moodEnergyLogDao = db.moodEnergyLogDao(),
                    focusReleaseLogDao = db.focusReleaseLogDao(),
                    medicationRefillDao = db.medicationRefillDao(),
                    weeklyReviewDao = db.weeklyReviewDao(),
                    dailyEssentialSlotCompletionDao = db.dailyEssentialSlotCompletionDao(),
                    attachmentDao = db.attachmentDao(),
                    medicationDao = db.medicationDao(),
                    medicationDoseDao = db.medicationDoseDao(),
                    medicationSlotDao = db.medicationSlotDao(),
                    medicationSlotOverrideDao = db.medicationSlotOverrideDao(),
                    medicationTierStateDao = db.medicationTierStateDao(),
                    logger = logger
                )
                return Device(db, healer, firestore, label)
            }

            private fun collectionNameFor(entityType: String): String = when (entityType) {
                "self_care_step" -> "self_care_steps"
                "self_care_log" -> "self_care_logs"
                "course" -> "courses"
                "course_completion" -> "course_completions"
                "leisure_log" -> "leisure_logs"
                else -> entityType + "s"
            }
        }
    }

    /**
     * In-memory stand-in for the shared Firestore subcollection state.
     * Tracks doc IDs per collection; supports put/wipe/collection reads
     * with set semantics (no doc data — the healer only cares about
     * which cloud_ids exist). Concurrent write hazards aren't a concern
     * because `runTest` serializes access within a single test.
     */
    private class SimulatedFirestore {
        private val state = mutableMapOf<String, MutableSet<String>>()

        fun put(collection: String, docId: String) {
            state.getOrPut(collection) { mutableSetOf() }.add(docId)
        }

        fun wipe(collection: String) {
            state[collection]?.clear()
        }

        fun collection(collection: String): Set<String> =
            state[collection]?.toSet() ?: emptySet()
    }
}
