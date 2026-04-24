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
 * Device A and device B both complete the same habit on the same
 * calendar day. After both completions land and A pulls, the habit's
 * streak must show 1, not 2 — same-day completions dedup in the streak
 * calculation regardless of how many rows Room holds.
 *
 * PrismTask's dedup lives in two places:
 *  1. **Insertion-time cap** in HabitRepository.completeHabit (line 149):
 *     the LOCAL completion path refuses to insert past the target
 *     frequency for a given day. Device A can only self-complete once.
 *  2. **Pull path** in SyncService: accepts rows from Firestore verbatim,
 *     including a second completion written by device B that shares the
 *     same `completed_date_local`. Room ends up with two rows for the day.
 *
 * The streak correctness guarantee then relies on StreakCalculator
 * collapsing same-day completions by `completedDate.toLocalDate()` when
 * it computes `strictStreak` / `resilientStreak`. If that collapse fails,
 * streak reads 2 instead of 1 and this test fails.
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
                    "localId" to 0L, // B's local id; A won't reuse it
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

            // 4. Device A pulls. Both completions land locally.
            syncService.pullRemoteChanges()
            harness.waitFor(
                timeout = 15.seconds,
                message = "device A's local Room pulled device B's completion"
            ) {
                val completions = database.habitCompletionDao()
                    .getCompletionsForHabitOnce(habitId)
                completions.size == 2
            }

            // 5. Streak must still read 1 — same-day dedup happens in
            //    StreakCalculator (read path), not on pull-insert.
            val streak = habitRepository.getResilientStreak(habitId)
            assertNotNull("Streak should be computable for a real habit", streak)
            assertEquals(
                "strictStreak must dedup same-day completions to 1 " +
                    "(got ${streak?.strictStreak})",
                1,
                streak?.strictStreak
            )
            assertEquals(
                "resilientStreak must dedup same-day completions to 1 " +
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
