package com.averycorp.prismtask.sync.fuzz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.MedicationEntity
import com.averycorp.prismtask.data.repository.MedicationRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Fuzz scenario 3 — medication + dose interleaved op sequence asserting
 * no orphan dose rows. Mirrors the #851/#853 shape: parent (medication)
 * deletion while a child (dose) insert is in-flight, but with the op
 * sequence randomized rather than hand-picked.
 *
 * Each step picks (insert med | update med | delete med | log dose against
 * a random live med). After every op, asserts FK integrity: every dose's
 * `medicationId` resolves to a live medication row.
 *
 * Replay: pin [SEED] = 31.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Fuzz03MedicationDoseFkSequenceTest : SyncFuzzScenarioBase() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Test
    fun medicationDoseInterleavedOps_neverProducesOrphanDose() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val random = Random(SEED)
            val medOps = SyncFuzzGenerator(
                random = Random(SEED + 1),
                opTypes = SyncFuzzOpType.entries.toSet(),
                devices = setOf(SyncFuzzDevice.A)
            ).generate(MED_OP_COUNT)

            val keyToMedId = mutableMapOf<String, Long>()
            val liveKeys = mutableSetOf<String>()

            // Interleave: every K-th op is a "log dose against a random
            // live med" instead of a med-level op. This produces the
            // dose-during-parent-flux shape that #851/#853 exposed.
            medOps.forEachIndexed { index, op ->
                try {
                    when (op.type) {
                        SyncFuzzOpType.INSERT -> {
                            val medId = medicationRepository.insert(
                                MedicationEntity(name = op.key, createdAt = 0L, updatedAt = 0L)
                            )
                            keyToMedId[op.key] = medId
                            liveKeys.add(op.key)
                        }
                        SyncFuzzOpType.UPDATE -> {
                            val medId = requireNotNull(keyToMedId[op.key])
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

                    // Every 3rd op: log a dose against a live med if any exist.
                    if (index % 3 == 0 && liveKeys.isNotEmpty()) {
                        val targetKey = liveKeys.random(random)
                        val targetMedId = requireNotNull(keyToMedId[targetKey])
                        medicationRepository.logDose(
                            medicationId = targetMedId,
                            slotKey = "fuzz-slot-$index",
                            takenAt = 1_000L * index
                        )
                    }

                    // FK invariant: every dose row points to a live medication.
                    assertNoOrphans(
                        children = database.medicationDoseDao().getAllOnce(),
                        parents = database.medicationDao().getAllOnce(),
                        childParentKey = { it.medicationId },
                        parentKey = { it.id },
                        opIndex = index,
                        op = op,
                        entityType = "medication_dose"
                    )
                } catch (t: Throwable) {
                    throw AssertionError(
                        "Fuzz sequence failed at op[$index] = $op (seed=$SEED). " +
                            "Replay with seed=$SEED.",
                        t
                    )
                }
            }
        }
    }

    companion object {
        private const val SEED = 31L
        private const val MED_OP_COUNT = 20
        private val TEST_TIMEOUT = 120.seconds
    }
}
