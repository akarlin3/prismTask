package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Test 14 — Rapid create/delete leaves no orphan Firestore doc.
 *
 * Creates a task locally, immediately deletes it, then triggers push.
 * Because the row never reached Firestore (cloud_id was null when delete
 * hit), `syncTracker.trackDelete` drops the metadata row entirely —
 * there's nothing to push for either operation. Assert Firestore has
 * zero task docs afterwards.
 *
 * This exercises the "delete before first push" edge case that
 * previously caused orphan docs when the create had already dispatched
 * to Firestore before the delete metadata update.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test14RapidCreateDeleteNoOrphanTest : SyncScenarioTestBase() {

    @Test
    fun rapidCreateDelete_leavesNoOrphanInFirestore() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // Create then delete within a few ms, all before any push.
            val taskId = taskRepository.addTask(title = "ephemeral-task")
            taskRepository.deleteTask(taskId)

            // Push — metadata shouldn't have anything to push for this
            // task (trackDelete cleared the row since cloud_id was null).
            val pushed = syncService.pushLocalChanges()

            // Firestore has no tasks — no orphan doc was left behind.
            assertEquals(
                "No Firestore task docs expected after rapid create/delete (pushed=$pushed)",
                0,
                harness.firestoreCount("tasks")
            )
        }
    }

    @Test
    fun createPushDeletePush_firestoreConvergesToEmpty() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // Variant: the "full round trip" rapid create-delete —
            // create + push, immediately delete + push. Firestore should
            // briefly have the doc then lose it.
            val taskId = taskRepository.addTask(title = "round-trip-task")
            val firstPush = syncService.pushLocalChanges()
            assertEquals(
                "Create should ship exactly one op; got $firstPush",
                1,
                firstPush
            )
            assertEquals(
                "Firestore has the task briefly",
                1,
                harness.firestoreCount("tasks")
            )

            taskRepository.deleteTask(taskId)
            val secondPush = syncService.pushLocalChanges()
            assertEquals(
                "Delete should ship exactly one op; got $secondPush",
                1,
                secondPush
            )

            // And the doc is gone from Firestore.
            harness.waitFor(
                timeout = 10.seconds,
                message = "task doc deleted from Firestore"
            ) {
                harness.firestoreCount("tasks") == 0
            }
        }
    }

    companion object {
        private val TEST_TIMEOUT = 60.seconds
    }
}
