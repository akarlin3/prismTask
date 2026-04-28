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
 * Fuzz scenario 2 — medication insert / update / delete op sequence on
 * device A, verifying Room and Firestore agree after push.
 *
 * Targets the same surface as #851 / #853 P0 (medication subsystem) but
 * with a randomized op sequence rather than a hand-picked one. Pre-fix,
 * a sequence of "insert med, push, delete med locally before pushDelete,
 * concurrent dose write from B" produced FK violations; after the
 * SyncService defensive guard, the same sequence shape is benign.
 *
 * Replay: pin [SEED] = 17 to reproduce the exact op sequence locally.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Fuzz02MedicationOpSequenceTest : SyncFuzzScenarioBase() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Test
    fun deviceA_medicationOpSequence_convergesWithFirestore() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val ops = SyncFuzzGenerator(
                random = Random(SEED),
                opTypes = SyncFuzzOpType.entries.toSet(),
                devices = setOf(SyncFuzzDevice.A)
            ).generate(SEQUENCE_LENGTH)

            val keyToMedId = mutableMapOf<String, Long>()
            val liveKeys = mutableSetOf<String>()

            runFuzzSequence(
                seed = SEED,
                ops = ops,
                applyOp = { op ->
                    when (op.type) {
                        SyncFuzzOpType.INSERT -> {
                            val medId = medicationRepository.insert(
                                MedicationEntity(
                                    name = op.key,
                                    notes = "fuzz-original",
                                    createdAt = 0L,
                                    updatedAt = 0L
                                )
                            )
                            keyToMedId[op.key] = medId
                            liveKeys.add(op.key)
                        }
                        SyncFuzzOpType.UPDATE -> {
                            val medId = requireNotNull(keyToMedId[op.key]) {
                                "fuzz key ${op.key} has no medId mapping"
                            }
                            val current = requireNotNull(database.medicationDao().getByIdOnce(medId))
                            medicationRepository.update(
                                current.copy(notes = "fuzz-update-${current.updatedAt}")
                            )
                        }
                        SyncFuzzOpType.DELETE -> {
                            val medId = requireNotNull(keyToMedId.remove(op.key))
                            val current = requireNotNull(database.medicationDao().getByIdOnce(medId))
                            medicationRepository.delete(current)
                            liveKeys.remove(op.key)
                        }
                    }
                },
                assertInvariants = { opIndex, op ->
                    assertRowCount(
                        actual = database.medicationDao().getAllOnce().size,
                        expected = liveKeys.size,
                        opIndex = opIndex,
                        op = op,
                        entityType = "medication"
                    )
                }
            )

            syncService.pushLocalChanges()
            harness.waitFor(
                timeout = 30.seconds,
                message = "Firestore medications converge to ${liveKeys.size} after fuzz push"
            ) {
                harness.firestoreCount("medications") == liveKeys.size
            }
            assertEquals(
                "Final Firestore medications count must match live-key set after seed=$SEED",
                liveKeys.size,
                harness.firestoreCount("medications")
            )
        }
    }

    companion object {
        private const val SEED = 17L
        private const val SEQUENCE_LENGTH = 30
        private val TEST_TIMEOUT = 120.seconds
    }
}
