package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Test 9 — Concurrent edit, last-write-wins.
 *
 * Device A and device B both update the same task. B's write lands in
 * Firestore with a later `updatedAt` timestamp. After A pulls, the
 * task's local `description` must equal B's value (B won), and exactly
 * one task doc exists in Firestore (same cloud_id, no duplicate).
 *
 * The "concurrency" in the real scenario is order-of-arrival flakiness;
 * for determinism this test serializes the writes (A first, B second)
 * and leans on the `updatedAt` timestamps to prove last-write-wins.
 * Firestore's `docRef.set(data)` (used by `SyncService.pushUpdate` at
 * line 1472) unconditionally overwrites the doc by ID, so two writes
 * to the same cloud_id collapse to one doc — no duplicate is possible
 * here. That makes the "no duplicate created" assertion automatic; the
 * interesting assertion is the content.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test9ConcurrentEditLastWriteWinsTest : SyncScenarioTestBase() {

    @Test
    fun concurrentEditSameTask_laterTimestampWins() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // 1. Create a task locally, push so it has a cloud_id.
            val taskId = taskRepository.addTask(
                title = "lww-task",
                description = "original"
            )
            syncService.pushLocalChanges()
            val cloudId = database.syncMetadataDao().getCloudId(taskId, "task")
            assertNotNull("Task cloud_id must be populated after push", cloudId)

            // 2. Device A edits the task with description "A"; push.
            //    addTask returns id; read the row back so we update against
            //    the current entity state (preserves other columns).
            val originalA = requireNotNull(database.taskDao().getTaskByIdOnce(taskId)) {
                "Task $taskId not found after addTask"
            }
            val aTimeMs = System.currentTimeMillis()
            taskRepository.updateTask(originalA.copy(description = "A", updatedAt = aTimeMs))
            syncService.pushLocalChanges()
            assertEquals(
                "Firestore should have A's edit after A's push",
                "A",
                harness.firestoreDoc("tasks", cloudId!!).getString("description")
            )

            // 3. Device B writes to the SAME doc with description "B" and a
            //    later `updatedAt`. This uses the same field shape as
            //    SyncMapper.taskToMap (SyncMapper.kt lines 36-76). We only
            //    populate the fields the mapToTask deserializer reads for
            //    the columns we're asserting on — other fields default.
            val bTimeMs = aTimeMs + 1_000L
            harness.writeAsDeviceB(
                subcollection = "tasks",
                docId = cloudId,
                fields = mapOf(
                    "title" to "lww-task",
                    "description" to "B",
                    "priority" to 0,
                    "isCompleted" to false,
                    "createdAt" to originalA.createdAt,
                    "updatedAt" to bTimeMs
                )
            )

            // Firestore has ONE doc (set-by-id overwrote the prior state).
            assertEquals(
                "No duplicate doc — same cloud_id means set() overwrote",
                1,
                harness.firestoreCount("tasks")
            )
            assertEquals(
                "Firestore doc now reflects B's write",
                "B",
                harness.firestoreDoc("tasks", cloudId).getString("description")
            )

            // 4. Device A pulls. Local Room converges to B's content.
            syncService.pullRemoteChanges()
            harness.waitFor(
                timeout = 15.seconds,
                message = "local task row reflects B's description"
            ) {
                database.taskDao().getTaskByIdOnce(taskId)?.description == "B"
            }

            // Final assertion for clarity.
            val converged = requireNotNull(database.taskDao().getTaskByIdOnce(taskId))
            assertEquals("lww-task", converged.title)
            assertEquals("B", converged.description)
        }
    }

    companion object {
        private val TEST_TIMEOUT = 90.seconds
    }
}
