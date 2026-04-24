package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 10 — Concurrent delete vs. offline edit.
 *
 * **SPEC:** device A goes offline and edits a task. Device B deletes
 * the task's Firestore doc. Device A reconnects and pushes its edit.
 * Delete wins: both devices end up without the task.
 *
 * **CURRENT BEHAVIOR (as of v1.5.3):** *edit* wins. The test is left as
 * `@Ignore` because the production conflict-resolution path is buggy
 * and fixing it is out of scope per PR2's guardrail ("do NOT modify
 * production sync code to make tests pass; flag and route product
 * fixes to a separate session").
 *
 * **Why edit wins on main:** `SyncService.pushUpdate` at
 * `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt:1472`
 * calls `docRef.set(data).await()` — bare `set`, no `SetOptions.merge()`,
 * no doc-exists precheck. Firestore's `set(data)` on a deleted doc
 * *re-creates* the doc. So after step 5's push:
 *   - `users/{uid}/tasks/{cloudId}` is back, carrying A's edited content
 *   - A's local Room retains the edit (pendingAction cleared)
 *   - B's next pull would re-insert the task locally with A's edit
 *
 * Delete doesn't win; it's silently undone. Users who delete a task on
 * one phone while another phone was offline and had edits to push will
 * see the task reappear after the offline phone comes back. That's a
 * data-correctness issue worth a real fix — candidates:
 *   - use `set(data, SetOptions.merge())` with server-side ServerValue
 *     markers, or
 *   - switch update path to `docRef.update()` which errors on missing
 *     doc (then pushUpdate catches, clears pendingAction, and drops the
 *     row locally — matching "delete wins"), or
 *   - maintain a tombstone collection and pushCreate checks it before
 *     creating.
 *
 * Implementation sketch once the fix lands (flip `@Ignore` to `@Test`):
 *  1. addTask + push → capture cloud_id via
 *     `database.syncMetadataDao().getCloudId(taskId, "task")`
 *  2. `harness.setDeviceAOffline()`
 *  3. A edits: `taskRepository.updateTask(task.copy(description = "edit"))`
 *  4. `harness.deleteAsDeviceB("tasks", cloudId)`
 *  5. `harness.setDeviceAOnline()`
 *  6. `syncService.pushLocalChanges()` — after the fix, this should
 *     *drop* A's pending update since the remote doc is gone.
 *  7. `syncService.pullRemoteChanges()`
 *  8. Assert `database.taskDao().getAllTasksOnce()` doesn't contain the
 *     task (row was cleaned up locally).
 *  9. Assert `harness.firestoreCount("tasks") == 0`.
 *
 * See the PR1 → PR2 → "sync-test followup" thread for the bug-tracking
 * conversation and the eventual production-code patch.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test10ConcurrentDeleteTest : SyncScenarioTestBase() {

    @Test
    @Ignore(
        "Current sync code fails this spec: SyncService.pushUpdate:1472 re-creates " +
            "the Firestore doc on missing-doc, so edit wins instead of delete. " +
            "Fixing requires a production change; test stays @Ignore until then."
    )
    fun concurrentDeleteVsEdit_deleteWins() {
        // Sketch in class KDoc. Flip @Ignore to @Test once the
        // pushUpdate-on-missing-doc behavior is corrected.
    }
}
