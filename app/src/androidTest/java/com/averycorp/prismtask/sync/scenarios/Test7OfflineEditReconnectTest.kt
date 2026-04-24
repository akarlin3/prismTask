package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.HabitEntity
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
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

            // Attempt to push while offline — writes queue on the
            // Firestore client's in-memory cache. This shouldn't hang
            // (per Firestore SDK docs: offline writes return immediately
            // via local cache) but we wrap in a timeout to be safe.
            runCatching { syncService.pushLocalChanges() }
            // Whether it returns a count or throws, the remote side must
            // NOT reflect offline writes yet.
            assertEquals(
                "Offline writes must not have reached Firestore yet",
                1,
                harness.firestoreCount("projects") // project still visible
            )
            assertEquals(
                "Offline task must not have reached Firestore yet",
                0,
                harness.firestoreCount("tasks")
            )
            assertEquals(
                "Offline habit must not have reached Firestore yet",
                0,
                harness.firestoreCount("habits")
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
