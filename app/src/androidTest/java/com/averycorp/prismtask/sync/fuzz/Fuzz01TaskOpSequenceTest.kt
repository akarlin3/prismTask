package com.averycorp.prismtask.sync.fuzz

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Smallest-viable fuzz scenario, gating the setup PR. Targets device A
 * task ops (insert / update / delete) only — single-entity, single-device,
 * no cross-device interleaving. The Tier A1 batch PR adds the
 * cross-device + medication/slot scenarios that motivated the audit.
 *
 * Asserts:
 *  1. Local Room row count matches the fuzz generator's running live-key set
 *     after every op (catches lost updates / phantom rows).
 *  2. After a final `pushLocalChanges`, Firestore has exactly the live keys
 *     remaining (catches orphan creates and missed deletes).
 *
 * Replay: pin [SEED] in the failing assertion; the generator is
 * deterministic per [SyncFuzzGeneratorTest].
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Fuzz01TaskOpSequenceTest : SyncFuzzScenarioBase() {

    @Test
    fun deviceA_taskOpSequence_convergesWithFirestore() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            val ops = SyncFuzzGenerator(
                random = Random(SEED),
                opTypes = SyncFuzzOpType.entries.toSet(),
                devices = setOf(SyncFuzzDevice.A)
            ).generate(SEQUENCE_LENGTH)

            val keyToTaskId = mutableMapOf<String, Long>()
            val liveKeys = mutableSetOf<String>()

            runFuzzSequence(
                seed = SEED,
                ops = ops,
                applyOp = { op ->
                    when (op.type) {
                        SyncFuzzOpType.INSERT -> {
                            val taskId = taskRepository.addTask(title = op.key)
                            keyToTaskId[op.key] = taskId
                            liveKeys.add(op.key)
                        }
                        SyncFuzzOpType.UPDATE -> {
                            val taskId = requireNotNull(keyToTaskId[op.key]) {
                                "fuzz key ${op.key} has no taskId mapping"
                            }
                            val current = requireNotNull(database.taskDao().getTaskByIdOnce(taskId)) {
                                "task $taskId missing locally before UPDATE op"
                            }
                            taskRepository.updateTask(
                                current.copy(description = "fuzz-update-${current.updatedAt}")
                            )
                        }
                        SyncFuzzOpType.DELETE -> {
                            val taskId = requireNotNull(keyToTaskId.remove(op.key)) {
                                "fuzz key ${op.key} has no taskId mapping"
                            }
                            taskRepository.deleteTask(taskId)
                            liveKeys.remove(op.key)
                        }
                    }
                },
                assertInvariants = { opIndex, op ->
                    assertRowCount(
                        actual = database.taskDao().getAllTasksOnce().size,
                        expected = liveKeys.size,
                        opIndex = opIndex,
                        op = op,
                        entityType = "task"
                    )
                }
            )

            // Push the entire sequence in one shot, then assert Firestore
            // converges to exactly the live keys.
            syncService.pushLocalChanges()
            harness.waitFor(
                timeout = 30.seconds,
                message = "Firestore tasks converge to ${liveKeys.size} after fuzz push"
            ) {
                harness.firestoreCount("tasks") == liveKeys.size
            }
            assertEquals(
                "Final Firestore count must match live-key set after seed=$SEED sequence",
                liveKeys.size,
                harness.firestoreCount("tasks")
            )
        }
    }

    companion object {
        private const val SEED = 42L
        private const val SEQUENCE_LENGTH = 30
        private val TEST_TIMEOUT = 120.seconds
    }
}
