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
 * Fuzz scenario 5 — rapid create/delete cycles with intermittent push.
 * Generalizes Test14's "rapid create-delete leaves no orphan" assertion
 * to the medication subsystem, with randomized push timing.
 *
 * Pre-fix history: rapid create-delete-create cycles before any push
 * could leave orphan Firestore docs if the create's metadata reached
 * Firestore between trackCreate and trackDelete. The audit's #851/#853
 * fan-out tightened these paths; this fuzz scenario asserts the
 * tightening holds under randomized timing.
 *
 * Replay: pin [SEED] = 89.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Fuzz05MedicationRapidCreateDeleteTest : SyncFuzzScenarioBase() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Test
    fun rapidCreateDeleteCycles_neverLeavesOrphanFirestore() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val random = Random(SEED)
            val keyToMedId = mutableMapOf<String, Long>()
            val liveKeys = mutableSetOf<String>()
            var keyCounter = 0

            repeat(CYCLE_COUNT) { cycle ->
                // Roll a small batch of inserts.
                val batchSize = random.nextInt(1, 4)
                repeat(batchSize) {
                    val key = "fuzz-$cycle-${keyCounter++}"
                    val medId = medicationRepository.insert(
                        MedicationEntity(name = key, createdAt = 0L, updatedAt = 0L)
                    )
                    keyToMedId[key] = medId
                    liveKeys.add(key)
                }

                // Maybe push.
                if (random.nextBoolean()) {
                    syncService.pushLocalChanges()
                }

                // Roll deletes against a random subset of live keys.
                val deleteCount = if (liveKeys.isEmpty()) 0 else random.nextInt(0, liveKeys.size + 1)
                val toDelete = liveKeys.shuffled(random).take(deleteCount).toList()
                for (key in toDelete) {
                    val medId = requireNotNull(keyToMedId.remove(key))
                    val current = requireNotNull(database.medicationDao().getByIdOnce(medId))
                    medicationRepository.delete(current)
                    liveKeys.remove(key)
                }

                // Maybe push.
                if (random.nextBoolean()) {
                    syncService.pushLocalChanges()
                }
            }

            // Final push to drain pending ops.
            syncService.pushLocalChanges()

            harness.waitFor(
                timeout = 30.seconds,
                message = "Firestore medications converge to ${liveKeys.size} after ${CYCLE_COUNT} cycles"
            ) {
                harness.firestoreCount("medications") == liveKeys.size
            }
            assertEquals(
                "Final Firestore must equal live-key set after rapid cycles (seed=$SEED)",
                liveKeys.size,
                harness.firestoreCount("medications")
            )
            assertEquals(
                "Local Room row count must equal live-key set",
                liveKeys.size,
                database.medicationDao().getAllOnce().size
            )
        }
    }

    companion object {
        private const val SEED = 89L
        private const val CYCLE_COUNT = 8
        private val TEST_TIMEOUT = 180.seconds
    }
}
