package com.averycorp.prismtask.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.AuthTokenPreferences
import com.averycorp.prismtask.data.preferences.BackendSyncPreferences
import com.averycorp.prismtask.data.remote.sync.BackendSyncService
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Part 8 — Edge Cases: offline + sync recovery.
 *
 * These tests exercise the offline path of the app: writes made with no
 * network must land in Room, be flagged as pending sync, and survive until a
 * subsequent successful [BackendSyncService.fullSync] clears them. They also
 * cover the kill-during-sync case: cancelling a sync mid-flight must leave
 * Room consistent and a retry must succeed with no duplicates.
 *
 * Network is simulated — the [FakePrismTaskApi] installed by
 * [TestNetworkModule] replaces the production Retrofit binding, so nothing
 * here touches the real backend. Toggling [FakePrismTaskApi.networkEnabled]
 * mimics losing/regaining connectivity.
 *
 * Sync tracking: in production, [com.averycorp.prismtask.data.remote.SyncTracker]
 * only records pending entries when a Firebase user id is present. We can't
 * stand up Firebase in an instrumentation test, so the tests insert the
 * `sync_metadata` rows directly through [SyncMetadataDao] — that's exactly
 * what the tracker would have written — and the tests verify the row is
 * present before the sync, gone after.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EdgeCaseOfflineTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var database: PrismTaskDatabase

    @Inject lateinit var taskRepository: TaskRepository

    @Inject lateinit var habitRepository: HabitRepository

    @Inject lateinit var syncMetadataDao: SyncMetadataDao

    @Inject lateinit var backendSyncService: BackendSyncService

    @Inject lateinit var fakeApi: FakePrismTaskApi

    @Inject lateinit var authTokenPreferences: AuthTokenPreferences

    @Inject lateinit var backendSyncPreferences: BackendSyncPreferences

    @Before
    fun setUp() {
        hiltRule.inject()
        runTest {
            // Pretend the user is signed into the FastAPI backend so
            // BackendSyncService.isConnected() returns true without touching a
            // real auth flow.
            authTokenPreferences.saveTokens(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token"
            )
            // Start from a clean pending state so tests don't see leftover rows.
            syncMetadataDao.deleteAll()
            fakeApi.networkEnabled = true
            fakeApi.pushedOperations.clear()
            fakeApi.pulledSinceParams.clear()
            fakeApi.onBeforePush = null
            fakeApi.onBeforePull = null
        }
    }

    @After
    fun tearDown() {
        runTest {
            fakeApi.onBeforePush = null
            fakeApi.onBeforePull = null
            database.clearAllTables()
            authTokenPreferences.clearTokens()
            backendSyncPreferences.clear()
        }
    }

    // region Helpers

    private suspend fun markPending(localId: Long, entityType: String, action: String = "create") {
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                localId = localId,
                entityType = entityType,
                cloudId = if (action == "create") "" else "cloud-$localId",
                pendingAction = action,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Run a full sync and — on success — clear every pending_action, which is
     * how the production Firebase [com.averycorp.prismtask.data.remote.SyncService]
     * treats a successful push. [BackendSyncService] doesn't consult
     * `sync_metadata` itself (it uses `updated_at > since` filtering), so
     * we model the clearing explicitly here to exercise the recovery contract.
     */
    private suspend fun syncAndClearPending(): Result<Unit> {
        val result = backendSyncService.fullSync()
        if (result.isSuccess) {
            syncMetadataDao.getPendingActions().forEach {
                syncMetadataDao.clearPendingAction(it.localId, it.entityType)
            }
        }
        return result.map { }
    }

    // endregion

    @Test
    fun testOfflineTaskCreate() = runTest {
        // Offline: creating a task should land locally and be tracked as
        // pending, while any attempt to sync must fail fast.
        fakeApi.networkEnabled = false

        val taskId = taskRepository.insertTask(
            TaskEntity(title = "Offline created task", priority = 2)
        )
        markPending(taskId, "task", action = "create")

        val stored = database.taskDao().getTaskByIdOnce(taskId)
        assertNotNull("Task should exist in Room after an offline insert", stored)
        assertEquals("Offline created task", stored!!.title)

        val pendingBefore = syncMetadataDao.getPendingActions()
        assertTrue(
            "Expected a pending sync entry for the offline task",
            pendingBefore.any { it.localId == taskId && it.entityType == "task" }
        )

        val offlineResult = backendSyncService.fullSync()
        assertTrue(
            "fullSync should fail while offline",
            offlineResult.isFailure
        )
        assertTrue(
            "Pending entry must survive a failed offline sync",
            syncMetadataDao.getPendingActions().any { it.localId == taskId }
        )
        assertTrue(
            "No push should have reached the server while offline",
            fakeApi.pushedOperations.isEmpty()
        )

        // Re-enable the network and drain the queue.
        fakeApi.networkEnabled = true
        val syncResult = syncAndClearPending()
        assertTrue("Sync should succeed once back online", syncResult.isSuccess)
        assertEquals(
            "Exactly one push should have been issued",
            1,
            fakeApi.pushedOperations.size
        )
        val pushedTaskOps = fakeApi.pushedOperations.first().operations
            .filter { it.entityType == "task" }
        assertTrue(
            "Pushed payload must include the offline-created task",
            pushedTaskOps.any { it.entityId == taskId }
        )
        val pendingAfter = syncMetadataDao.getPendingActions()
        assertFalse(
            "Pending entry should be cleared after a successful sync",
            pendingAfter.any { it.localId == taskId && it.entityType == "task" }
        )
    }

    @Test
    fun testOfflineTaskEdit() = runTest {
        // Seed a task and treat it as already synced (cloudId set, no pending
        // action). This is the initial state for an "edit while offline" run.
        val taskId = taskRepository.insertTask(
            TaskEntity(title = "Initial title", priority = 1)
        )
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                localId = taskId,
                entityType = "task",
                cloudId = "cloud-$taskId",
                pendingAction = null,
                lastSyncedAt = System.currentTimeMillis()
            )
        )

        fakeApi.networkEnabled = false

        val original = database.taskDao().getTaskByIdOnce(taskId)!!
        taskRepository.updateTask(original.copy(title = "Edited offline"))
        markPending(taskId, "task", action = "update")

        val updated = database.taskDao().getTaskByIdOnce(taskId)
        assertEquals(
            "Local Room must reflect the offline edit immediately",
            "Edited offline",
            updated?.title
        )
        assertTrue(
            "Expected a pending update entry for the offline edit",
            syncMetadataDao.getPendingActions().any {
                it.localId == taskId && it.entityType == "task" && it.pendingAction == "update"
            }
        )

        fakeApi.networkEnabled = true
        val result = syncAndClearPending()
        assertTrue("Sync should succeed once online", result.isSuccess)
        assertTrue(
            "Pending update should be cleared after a successful sync",
            syncMetadataDao.getPendingActions().none { it.localId == taskId }
        )
    }

    @Test
    fun testOfflineTaskComplete() = runTest {
        val taskId = taskRepository.insertTask(
            TaskEntity(
                title = "Mark complete offline",
                dueDate = System.currentTimeMillis()
            )
        )
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                localId = taskId,
                entityType = "task",
                cloudId = "cloud-$taskId",
                pendingAction = null,
                lastSyncedAt = System.currentTimeMillis()
            )
        )

        fakeApi.networkEnabled = false
        taskRepository.completeTask(taskId)
        markPending(taskId, "task", action = "update")

        val completed = database.taskDao().getTaskByIdOnce(taskId)
        assertNotNull(completed)
        assertTrue(
            "Task must be marked complete in Room even while offline",
            completed!!.isCompleted
        )
        assertNotNull(
            "completedAt should be set by completeTask()",
            completed.completedAt
        )
        assertTrue(
            "Completion should leave a pending sync entry",
            syncMetadataDao.getPendingActions().any { it.localId == taskId }
        )

        fakeApi.networkEnabled = true
        val result = syncAndClearPending()
        assertTrue("Sync should succeed once online", result.isSuccess)
        assertTrue(
            "Pending entry should be cleared after the completion syncs",
            syncMetadataDao.getPendingActions().none { it.localId == taskId }
        )
    }

    @Test
    fun testOfflineHabitCompletion() = runTest {
        val habitId = habitRepository.addHabit(
            HabitEntity(name = "Stretch", icon = "\u2728", color = "#FF5733")
        )
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                localId = habitId,
                entityType = "habit",
                cloudId = "cloud-habit-$habitId",
                pendingAction = null,
                lastSyncedAt = System.currentTimeMillis()
            )
        )

        fakeApi.networkEnabled = false
        val today = System.currentTimeMillis()
        habitRepository.completeHabit(habitId, today)

        val completions = database.habitCompletionDao().getCompletionsForHabitOnce(habitId)
        assertEquals(
            "Offline habit completion should be persisted locally",
            1,
            completions.size
        )
        val completionId = completions.first().id
        markPending(completionId, "habit_completion", action = "create")

        assertTrue(
            "Habit completion should be tracked as pending",
            syncMetadataDao.getPendingActions().any {
                it.localId == completionId && it.entityType == "habit_completion"
            }
        )

        fakeApi.networkEnabled = true
        val result = syncAndClearPending()
        assertTrue("Sync should succeed once online", result.isSuccess)
        assertTrue(
            "Pending completion entry should be cleared after sync",
            syncMetadataDao.getPendingActions().none {
                it.localId == completionId && it.entityType == "habit_completion"
            }
        )
        // The underlying Room row must still be there — clearing pending is a
        // metadata-only operation, not a delete.
        assertEquals(
            "The completion row itself must not be deleted by the sync",
            1,
            database.habitCompletionDao().getCompletionsForHabitOnce(habitId).size
        )
    }

    @Test
    fun testKillDuringSync() = runTest {
        // Create a handful of pending entities so the push has real work to do.
        val firstId = taskRepository.insertTask(TaskEntity(title = "Kill-test A"))
        val secondId = taskRepository.insertTask(TaskEntity(title = "Kill-test B"))
        markPending(firstId, "task", action = "create")
        markPending(secondId, "task", action = "create")

        val snapshotBefore = database.taskDao().getAllTasksOnce().map { it.id to it.title }.toSet()
        val pendingBefore = syncMetadataDao.getPendingActions().map { it.localId to it.entityType }.toSet()

        // Arrange: the next syncPush will hang long enough for us to cancel the
        // job, simulating a process kill / force-stop in the middle of sync.
        val pushStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        fakeApi.onBeforePush = {
            pushStarted.complete(Unit)
            // Wait long enough that cancelAndJoin below will definitely
            // catch the coroutine mid-flight.
            delay(5_000)
        }

        val scope = CoroutineScope(Dispatchers.IO + Job())
        val syncJob = scope.launch {
            runCatching { backendSyncService.fullSync().getOrThrow() }
        }
        pushStarted.await()
        syncJob.cancelAndJoin()

        // The sync was interrupted: no push should have completed, nothing
        // should have been cleared, and no data should have been lost.
        assertTrue(
            "Cancelled push must not record a completed operation",
            fakeApi.pushedOperations.isEmpty()
        )
        val snapshotMid = database.taskDao().getAllTasksOnce().map { it.id to it.title }.toSet()
        assertEquals(
            "Tasks must not be lost or duplicated by an interrupted sync",
            snapshotBefore,
            snapshotMid
        )
        val pendingMid = syncMetadataDao.getPendingActions().map { it.localId to it.entityType }.toSet()
        assertEquals(
            "Pending entries must survive a cancelled sync",
            pendingBefore,
            pendingMid
        )

        // Retry: the second attempt should complete cleanly.
        fakeApi.onBeforePush = null
        yield()
        val retryResult = syncAndClearPending()
        assertTrue(
            "Retry after interruption should succeed",
            retryResult.isSuccess
        )
        assertEquals(
            "Exactly one push should have landed on retry",
            1,
            fakeApi.pushedOperations.size
        )

        // No duplicate tasks — still the same rows, same ids, same titles.
        val snapshotAfter = database.taskDao().getAllTasksOnce().map { it.id to it.title }.toSet()
        assertEquals(
            "No duplicate tasks should be introduced by the retry",
            snapshotBefore,
            snapshotAfter
        )
        val pendingAfter = syncMetadataDao.getPendingActions()
        assertTrue(
            "All pending entries should be cleared after the successful retry",
            pendingAfter.isEmpty()
        )

        // Extra sanity: the tasks we inserted before the kill are still there.
        assertNotNull(database.taskDao().getTaskByIdOnce(firstId))
        assertNotNull(database.taskDao().getTaskByIdOnce(secondId))
        // And nothing bogus was left behind in sync_metadata.
        assertNull(
            "Pending entry for first task should be fully cleared",
            syncMetadataDao.get(firstId, "task")?.pendingAction
        )
        assertNull(
            "Pending entry for second task should be fully cleared",
            syncMetadataDao.get(secondId, "task")?.pendingAction
        )
    }
}
