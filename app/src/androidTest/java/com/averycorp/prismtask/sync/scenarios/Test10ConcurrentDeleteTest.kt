package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Test 10 — Concurrent delete vs. offline edit: delete wins.
 *
 * Device A goes offline and edits a task. Device B deletes the task's
 * Firestore doc. Device A reconnects and pushes its edit. After the
 * 2026-04-24 delete-wins fix, `pushUpdate` uses `docRef.update(data)`
 * rather than `docRef.set(data)`, so the update throws NOT_FOUND /
 * FAILED_PRECONDITION when the remote doc is missing; `pushUpdate`
 * catches that and routes the orphan through
 * [SyncService.processRemoteDeletions] — the local row is hard-deleted
 * and its `sync_metadata` entry cleared.
 *
 * Pre-fix history: prior to this fix, `docRef.set(data)` on a
 * non-existent path silently created the doc, so A's edit re-created
 * the deletion B had just performed. Edit won, delete lost. See the
 * `fix(sync): pushUpdate conflict resolution` PR for the bug writeup
 * and `SyncService.pushUpdate` comment for the implementation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test10ConcurrentDeleteTest : SyncScenarioTestBase() {

    @Test
    fun concurrentDeleteVsEdit_deleteWins() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // 1. Create a task, push it → has a stable cloud_id.
            val taskId = taskRepository.addTask(
                title = "doomed-task",
                description = "original"
            )
            syncService.pushLocalChanges()
            val cloudId = database.syncMetadataDao().getCloudId(taskId, "task")
            assertNotNull("Task cloud_id must be populated after push", cloudId)
            assertEquals(
                "Firestore should have the baseline task",
                1,
                harness.firestoreCount("tasks")
            )

            // 2. Device A goes offline, queues a local update.
            //    `pushUpdate` isn't driven yet — the pending update sits
            //    in sync_metadata with `pendingAction = "update"`.
            harness.setDeviceAOffline()
            val original = requireNotNull(database.taskDao().getTaskByIdOnce(taskId))
            taskRepository.updateTask(
                original.copy(
                    description = "A's offline edit",
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 3. Device B deletes the Firestore doc.
            harness.deleteAsDeviceB("tasks", cloudId!!)

            // 4. Device A reconnects and pushes. After the fix, the push
            //    finds the remote doc missing and cleans up the local row
            //    + sync_metadata instead of re-creating it.
            harness.setDeviceAOnline()
            syncService.pushLocalChanges()

            // 5. Assertions — delete wins.
            harness.waitFor(
                timeout = 15.seconds,
                message = "local task row removed on A after remote-deleted push"
            ) {
                database.taskDao().getTaskByIdOnce(taskId) == null
            }
            assertFalse(
                "Local Room must not retain the deleted task",
                database.taskDao().getAllTasksOnce().any { it.id == taskId }
            )
            assertEquals(
                "Firestore must stay at 0 tasks — A's push did NOT resurrect",
                0,
                harness.firestoreCount("tasks")
            )
            // sync_metadata for this task is cleared so the next push
            // iteration doesn't re-process the stale row.
            assertEquals(
                "sync_metadata cleaned up for the deleted task",
                null,
                database.syncMetadataDao().getCloudId(taskId, "task")
            )
        }
    }

    companion object {
        private val TEST_TIMEOUT = 90.seconds
    }
}
