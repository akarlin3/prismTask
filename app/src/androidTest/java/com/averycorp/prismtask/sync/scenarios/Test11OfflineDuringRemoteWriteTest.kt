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
 * Test 11 — Offline during remote write.
 *
 * Device A goes offline. While A is offline, "device B" (direct Firestore
 * write via harness) creates a new task. Device A reconnects and pulls.
 * Assert: device A's local Room has the task written by device B.
 *
 * This is the baseline "pull works" scenario — if this fails, most other
 * scenarios can't be trusted either.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test11OfflineDuringRemoteWriteTest : SyncScenarioTestBase() {

    @Test
    fun offlineDuringRemoteWrite_localPullsOnReconnect() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // Device A has no pending local changes — starts clean.
            val initialPush = syncService.pushLocalChanges()
            assertEquals("No baseline ops to push", 0, initialPush)

            // A goes offline.
            harness.setDeviceAOffline()

            // B writes a task directly to Firestore. Shape matches
            // SyncMapper.taskToMap() well enough to round-trip.
            val nowMs = System.currentTimeMillis()
            val deviceBDocId = "remote-task-$nowMs"
            harness.writeAsDeviceB(
                subcollection = "tasks",
                docId = deviceBDocId,
                fields = mapOf(
                    "title" to "written-by-device-B",
                    "description" to null,
                    "priority" to 0,
                    "isCompleted" to false,
                    "createdAt" to nowMs,
                    "updatedAt" to nowMs
                )
            )
            // A reconnects + pulls.
            harness.setDeviceAOnline()
            // Give the Firestore client a moment to re-establish; then pull.
            syncService.pullRemoteChanges()

            // Local Room should now have the remote task.
            harness.waitFor(
                timeout = 15.seconds,
                message = "local DB pulled device B's task"
            ) {
                val tasks = database.taskDao().getAllTasksOnce()
                tasks.any { it.title == "written-by-device-B" }
            }
        }
    }

    companion object {
        private val TEST_TIMEOUT = 60.seconds
    }
}
