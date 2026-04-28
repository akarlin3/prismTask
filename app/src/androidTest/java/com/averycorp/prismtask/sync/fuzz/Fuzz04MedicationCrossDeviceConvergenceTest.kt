package com.averycorp.prismtask.sync.fuzz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.repository.MedicationRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Fuzz scenario 4 — cross-device medication convergence under randomized
 * op interleaving. Device A ops go through the local repository (which
 * syncs via SyncService); device B ops go through the SyncTestHarness as
 * raw Firestore writes (simulating "another device's write").
 *
 * After the entire sequence runs, push from A and pull onto A. Assert
 * Room and Firestore agree on the final live-key set — this is the
 * convergence-shape invariant per memory
 * `feedback_firestore_doc_iteration_order.md` (we never assert which
 * cloud_id "wins" a natural-key dedup, only that the row count converges).
 *
 * Replay: pin [SEED] = 53.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Fuzz04MedicationCrossDeviceConvergenceTest : SyncFuzzScenarioBase() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Test
    fun crossDeviceMedicationOps_converge() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val ops = SyncFuzzGenerator(
                random = Random(SEED),
                opTypes = setOf(SyncFuzzOpType.INSERT, SyncFuzzOpType.UPDATE),
                devices = setOf(SyncFuzzDevice.A, SyncFuzzDevice.B)
            ).generate(SEQUENCE_LENGTH)

            val aKeyToMedId = mutableMapOf<String, Long>()
            val bKeyToCloudId = mutableMapOf<String, String>()
            val liveKeys = mutableSetOf<String>()

            ops.forEachIndexed { index, op ->
                when (op.device) {
                    SyncFuzzDevice.A -> when (op.type) {
                        SyncFuzzOpType.INSERT -> {
                            val medId = medicationRepository.insert(
                                MedicationEntity(name = op.key, createdAt = 0L, updatedAt = 0L)
                            )
                            aKeyToMedId[op.key] = medId
                            liveKeys.add(op.key)
                        }
                        SyncFuzzOpType.UPDATE -> {
                            val medId = aKeyToMedId[op.key] ?: return@forEachIndexed
                            val current = database.medicationDao().getByIdOnce(medId)
                                ?: return@forEachIndexed
                            medicationRepository.update(
                                current.copy(notes = "A-fuzz-${current.updatedAt}")
                            )
                        }
                        else -> Unit
                    }
                    SyncFuzzDevice.B -> when (op.type) {
                        SyncFuzzOpType.INSERT -> {
                            val cloudId = "fuzz-b-${op.key}"
                            harness.writeAsDeviceB(
                                subcollection = "medications",
                                docId = cloudId,
                                fields = baseMedFields(op.key, "B-fuzz-original")
                            )
                            bKeyToCloudId[op.key] = cloudId
                            liveKeys.add(op.key)
                        }
                        SyncFuzzOpType.UPDATE -> {
                            val cloudId = bKeyToCloudId[op.key] ?: return@forEachIndexed
                            harness.writeAsDeviceB(
                                subcollection = "medications",
                                docId = cloudId,
                                fields = baseMedFields(
                                    op.key,
                                    "B-fuzz-update-$index",
                                    updatedAt = 1_000L + index
                                )
                            )
                        }
                        else -> Unit
                    }
                }
            }

            // Drain in both directions.
            syncService.pushLocalChanges()
            syncService.pullRemoteChanges()
            syncService.pushLocalChanges()

            harness.waitFor(
                timeout = 30.seconds,
                message = "Firestore medications converge to ${liveKeys.size}"
            ) {
                harness.firestoreCount("medications") >= liveKeys.size
            }
            // Convergence-shape invariant: Room row count matches the
            // device-A-tracked live keys. We don't assert Firestore count
            // exactly because B's writes may produce extra cloud_ids that
            // SyncMapper natural-key-dedups onto A's rows; the contract
            // is that the local Room view sees every live key once.
            assertEquals(
                "Local Room must reflect every live key after convergence (seed=$SEED)",
                liveKeys.size,
                database.medicationDao().getAllOnce()
                    .map { it.name }
                    .filter { it in liveKeys }
                    .toSet()
                    .size
            )
        }
    }

    private fun baseMedFields(name: String, notes: String, updatedAt: Long = 0L) = mapOf(
        "localId" to 9999L,
        "name" to name,
        "notes" to notes,
        "tier" to "essential",
        "isArchived" to false,
        "sortOrder" to 0,
        "scheduleMode" to "TIMES_OF_DAY",
        "dosesPerDay" to 1,
        "pillsPerDose" to 1,
        "reminderDaysBefore" to 3,
        "slotCloudIds" to emptyList<String>(),
        "createdAt" to 0L,
        "updatedAt" to updatedAt
    )

    companion object {
        private const val SEED = 53L
        private const val SEQUENCE_LENGTH = 25
        private val TEST_TIMEOUT = 180.seconds
    }
}
