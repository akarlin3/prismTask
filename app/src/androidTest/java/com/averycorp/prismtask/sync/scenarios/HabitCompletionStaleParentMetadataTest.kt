package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.HabitEntity
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * P0 sync audit PR-A regression gate.
 *
 * Scenario: A creates and pushes a habit, then deletes it locally before
 * the push-delete reaches Firestore. `HabitRepository.deleteHabit` ran
 * `syncTracker.trackDelete(...)` (which set `pending_action='delete'` on
 * `sync_metadata` but left the metadata row in place — `pushDelete`
 * needs the cloud_id to find the Firestore target) followed by
 * `habitDao.deleteById(...)`. The `habits` row is gone but the
 * `sync_metadata` row still maps `cloud_id → local_id`.
 *
 * Meanwhile B writes a `habit_completion` for that habit's cloud_id.
 * Pre-fix, A's pull resolved `habitLocalId` from sync_metadata, called
 * `habitCompletionDao.insert(...)`, and Room threw
 * `SQLiteConstraintException: FOREIGN KEY constraint failed` (Test 3,
 * Session 1, 2026-04-27). The exception was swallowed by
 * `pullCollection`'s try/catch, surfaced only as `pull.apply | … |
 * status=failed` in logcat, and `sync.completed` reported `success`.
 *
 * Post-fix: the defensive guard at `SyncService.kt` (the
 * `habitDao.getHabitByIdOnce(habitLocalId) == null` check before insert)
 * detects the stale-parent state and treats it as a transient skip.
 * No exception is thrown; the eventual pushDelete propagates the
 * Firestore tombstone and the orphan completion is reaped by
 * `processRemoteDeletions` on the next pull cycle.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HabitCompletionStaleParentMetadataTest : SyncScenarioTestBase() {

    @Test
    fun habitCompletionPull_withStaleParentMetadata_skipsCleanly() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // 1. Create habit on A and push so its cloud_id is registered.
            val habitId = habitRepository.addHabit(
                HabitEntity(
                    name = "Hydrate",
                    targetFrequency = 1,
                    frequencyPeriod = "daily"
                )
            )
            syncService.pushLocalChanges()

            val habitCloudId = database.syncMetadataDao().getCloudId(habitId, "habit")
            assertNotNull("habit cloud_id populated after push", habitCloudId)
            assertEquals(
                "Firestore has exactly one habit doc",
                1,
                harness.firestoreCount("habits")
            )

            // 2. Delete habit locally — habits row is removed, sync_metadata
            // pending_action goes to 'delete' but the cloud_id mapping
            // remains until pushDelete actually runs (skipped here).
            habitRepository.deleteHabit(habitId)
            assertEquals(
                "habits row gone after deleteHabit",
                null,
                database.habitDao().getHabitByIdOnce(habitId)
            )
            assertEquals(
                "sync_metadata still resolves the now-stale cloud_id → local_id",
                habitId,
                database.syncMetadataDao().getLocalId(habitCloudId!!, "habit")
            )

            // 3. B writes a habit_completion targeting that habit's cloud_id.
            // Pre-fix this would FK-explode on A's pull.
            val nowMs = System.currentTimeMillis()
            harness.writeAsDeviceB(
                subcollection = "habit_completions",
                docId = "completion-from-b-after-stale-parent",
                fields = mapOf(
                    "habitCloudId" to habitCloudId,
                    "completedDate" to nowMs,
                    "completedAt" to nowMs,
                    "completedDateLocal" to "2026-04-27",
                    "createdAt" to nowMs,
                    "updatedAt" to nowMs
                )
            )

            // 4. A pulls. Must not throw. Defensive guard detects stale
            // parent and treats as transient skip.
            syncService.pullRemoteChanges()

            // 5. No habit_completions row was inserted (nothing to insert
            // safely against the missing parent). pull.summary may have
            // logged status=warning (skipped>0) but pullRemoteChanges
            // returned normally without an unhandled exception.
            assertEquals(
                "no habit_completions row inserted for the date the guard was tested with",
                0,
                database.habitCompletionDao()
                    .getCompletionCountForDateLocalOnce(habitId, "2026-04-27")
            )
            // sync_metadata for the completion is also absent — the
            // handler returned false BEFORE writing metadata.
            assertEquals(
                "no sync_metadata for the skipped completion",
                null,
                database.syncMetadataDao().getLocalId(
                    "completion-from-b-after-stale-parent",
                    "habit_completion"
                )
            )
        }
    }

    companion object {
        private val TEST_TIMEOUT = 90.seconds
    }
}
