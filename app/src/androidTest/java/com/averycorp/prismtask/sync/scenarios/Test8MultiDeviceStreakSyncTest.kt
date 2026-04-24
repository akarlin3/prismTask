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
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/**
 * Test 8 — Multi-device same-day habit completions dedup in the streak.
 *
 * Spec: device A and device B both complete the same habit on the same
 * calendar day. After A pulls B's completion, the habit's streak must
 * read 1, not 2.
 *
 * Production guarantees this via **pull-path natural-key dedup** at
 * `SyncService.kt:1681-1693`: when A pulls a `habit_completion` whose
 * `(habitId, completedDateLocal)` already matches an existing local row,
 * `pullCollection` upserts the `sync_metadata` mapping for the new
 * cloud_id onto A's existing local row instead of inserting a second
 * row. `StreakCalculator` sees one completion → streak = 1.
 *
 * Earlier draft of this test asserted post-pull `completions.size == 2`,
 * which never converges because of this dedup; that was a wrong mental
 * model of the pull pipeline (recorded in the v0 KDoc as Hypothesis A
 * "field-shape mismatch" / Hypothesis B "cursor-skip on equal
 * timestamps"). Audit ruled both out: SyncMapper push/pull use identical
 * field names (`localId`, `habitCloudId`, `completedDate`,
 * `completedDateLocal`, `completedAt`, `notes`), and `pullCollection`
 * does a full `userCollection(name).get()` snapshot fetch — no cursor.
 *
 * **Note on `sync_metadata.cloud_id` after dedup:** the dedup upsert
 * uses primary key `(local_id, entity_type)`, so it overwrites the
 * row's `cloud_id` rather than adding a second mapping. When the pull
 * iterates both A's own doc (`cidA`) and B's doc (`deviceBDocId`),
 * whichever is processed second wins the `cloud_id` slot. Firestore
 * `userCollection.get()` doesn't guarantee iteration order across
 * runs, so this test does NOT assert which cloud_id ends up mapped —
 * the contract Test 8 enforces is "Room row count + streak", not
 * sync_metadata cloud_id selection.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test8MultiDeviceStreakSyncTest : SyncScenarioTestBase() {

    @Test
    fun multiDeviceCompletionsSameDay_streakIsOneNotTwo() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            requireSignedIn()

            // 1. Create a habit locally; push so it has a stable cloud_id
            //    device B can reference.
            val habitId = habitRepository.addHabit(
                HabitEntity(name = "shared-streak-habit")
            )
            val pushed = syncService.pushLocalChanges()
            assertEquals("Habit should have been pushed once", 1, pushed)

            val habitCloudId = database.syncMetadataDao().getCloudId(habitId, "habit")
            assertNotNull(
                "Habit cloud_id must be populated after push",
                habitCloudId
            )

            // 2. Device A self-completes. The HabitRepository insertion cap
            //    lets through exactly one completion for today.
            val nowMs = System.currentTimeMillis()
            habitRepository.completeHabit(habitId = habitId, date = nowMs)
            syncService.pushLocalChanges()
            assertEquals(
                "Device A's completion should be the only Firestore doc so far",
                1,
                harness.firestoreCount("habit_completions")
            )

            // 3. Device B writes a second completion for the SAME habit on
            //    the SAME calendar day, using the shape that
            //    SyncMapper.habitCompletionToMap produces (SyncMapper.kt
            //    lines 291-326).
            val localDate = Instant.ofEpochMilli(nowMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            val deviceBDocId = "deviceB-completion-$nowMs"
            harness.writeAsDeviceB(
                subcollection = "habit_completions",
                docId = deviceBDocId,
                fields = mapOf(
                    "localId" to 0L,
                    "habitCloudId" to habitCloudId,
                    "completedDate" to nowMs,
                    "completedDateLocal" to localDate,
                    "completedAt" to nowMs,
                    "notes" to null
                )
            )
            assertEquals(
                "Firestore should now carry both devices' completions",
                2,
                harness.firestoreCount("habit_completions")
            )

            // 4. Device A pulls. The pull-path natural-key dedup at
            //    SyncService.kt:1681-1693 keeps Room at exactly 1 row —
            //    B's incoming doc collides on (habitId, completedDateLocal)
            //    and only the sync_metadata mapping is updated.
            syncService.pullRemoteChanges()

            val completions = database.habitCompletionDao()
                .getCompletionsForHabitOnce(habitId)
            assertEquals(
                "Pull-path natural-key dedup keeps Room at 1 row even though " +
                    "Firestore has 2 completion docs for the same day",
                1,
                completions.size
            )

            // 5. Streak reads 1 — same-day completions never produce a
            //    streak of 2 regardless of multi-device write timing.
            val streak = habitRepository.getResilientStreak(habitId)
            assertNotNull("Streak should be computable for a real habit", streak)
            assertEquals(
                "strictStreak must be 1 for one logical day's completion " +
                    "(got ${streak?.strictStreak})",
                1,
                streak?.strictStreak
            )
            assertEquals(
                "resilientStreak must be 1 for one logical day's completion " +
                    "(got ${streak?.resilientStreak})",
                1,
                streak?.resilientStreak
            )
        }
    }

    companion object {
        private val TEST_TIMEOUT = 90.seconds
    }
}
