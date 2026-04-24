package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.HabitEntity
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Test 7 — Offline edit, reconnect.
 *
 * Device A goes offline, performs three local mutations (create task,
 * create habit, delete project), comes back online, and pushes. Assert:
 *  - Firestore has the new task (create propagated)
 *  - Firestore has the new habit (create propagated)
 *  - Firestore no longer has the project (delete propagated)
 *  - No duplicates
 *
 * "Device B" in this scenario is just "whatever would read Firestore after"
 * — we don't need a separate local DB to prove the writes landed; if
 * Firestore has the expected docs, any device subscribing would converge.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test7OfflineEditReconnectTest : SyncScenarioTestBase() {

    @Test
    fun offlineCreateCreateDelete_allPropagateOnReconnect() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // Baseline: create a project, push it so it exists in Firestore
            // and has a cloud_id. This is the project we'll delete offline.
            val projectId = projectRepository.addProject(name = "doomed-project")
            val baselinePushed = syncService.pushLocalChanges()
            assertTrue(
                "Baseline push should have shipped the project; got $baselinePushed",
                baselinePushed >= 1
            )
            assertEquals(
                "Firestore should show the baseline project",
                1,
                harness.firestoreCount("projects")
            )

            // Device A goes offline — subsequent repo calls still mark
            // rows dirty in sync_metadata; the push attempt will queue in
            // memory and not reach the Firestore emulator until reconnect.
            harness.setDeviceAOffline()

            // Three offline mutations.
            taskRepository.addTask(title = "offline-task")
            habitRepository.addHabit(HabitEntity(name = "offline-habit"))
            val project = requireNotNull(database.projectDao().getProjectByIdOnce(projectId)) {
                "Baseline project not found by id $projectId"
            }
            projectRepository.deleteProject(project)

            // Don't call `syncService.pushLocalChanges()` while offline.
            // `docRef.set(data).await()` hangs indefinitely against a
            // network-disabled Firestore client when persistenceEnabled=false
            // (the harness disables persistence per
            // `SyncTestHarness.getOrCreateDeviceBApp`). The first run of
            // this test timed out at 90 s on that call.
            //
            // Read via device B's client — harness.firestoreCount uses
            // device A, which is currently offline and would serve from
            // an empty in-memory cache (persistence is disabled).
            val usersColl = harness.deviceBFirestore
                .collection("users")
                .document(harness.userId)
            assertEquals(
                "Offline writes must not have reached Firestore yet " +
                    "(project still visible: baseline push landed; delete not propagated)",
                1,
                usersColl.collection("projects").get().await().size(),
            )
            assertEquals(
                "Offline task must not have reached Firestore yet",
                0,
                usersColl.collection("tasks").get().await().size(),
            )
            assertEquals(
                "Offline habit must not have reached Firestore yet",
                0,
                usersColl.collection("habits").get().await().size(),
            )

            // Reconnect and push.
            harness.setDeviceAOnline()
            val reconnectPushed = syncService.pushLocalChanges()
            assertTrue(
                "Reconnect push should have shipped at least 3 pending ops " +
                    "(task create, habit create, project delete); got $reconnectPushed",
                reconnectPushed >= 3
            )

            // Firestore state converged.
            harness.waitFor(timeout = 15.seconds, message = "create+create+delete propagated") {
                harness.firestoreCount("tasks") == 1 &&
                    harness.firestoreCount("habits") == 1 &&
                    harness.firestoreCount("projects") == 0
            }

            // No duplicates of the task or habit.
            val tasks = harness.firestoreAllDocs("tasks")
            assertEquals("Exactly one task doc expected", 1, tasks.size)
            assertEquals("offline-task", tasks[0].getString("title"))

            val habits = harness.firestoreAllDocs("habits")
            assertEquals("Exactly one habit doc expected", 1, habits.size)
            assertEquals("offline-habit", habits[0].getString("name"))

            // Project delete landed — Firestore has 0 project docs.
            assertEquals(
                "Project delete should have been applied to Firestore",
                0,
                harness.firestoreCount("projects")
            )
        }
    }

    companion object {
        private val TEST_TIMEOUT = 90.seconds
    }
}
