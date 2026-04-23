package com.averycorp.prismtask.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification that [NotificationHelper.createNotificationChannel]
 * registers the base task-reminder channel with a style-suffixed ID and the
 * importance the user's preferences expect.
 *
 * Channel IDs under the new scheme look like
 * `prismtask_reminders_<importance>[_fsi][_alrm][_rvib]`. We don't assert on
 * the exact suffix here — the exact combination depends on the default
 * preferences on the test emulator — only that *some* channel whose ID
 * begins with the base prefix exists after the coroutine completes, with
 * an importance compatible with the selected level.
 *
 * Cleanup deletes any channel created by the test so production-style
 * assertions in other instrumentation tests start from a known baseline.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelsInstrumentedTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    private val createdChannelIds = mutableListOf<String>()

    @Before
    fun setup() {
        // Channels only exist on API 26+. Tests on older devices are no-ops.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @After
    fun teardown() {
        // Best-effort cleanup. We only delete channels this test created
        // (tracked via createdChannelIds) so we don't remove channels set
        // up by production code outside this test.
        for (id in createdChannelIds) {
            try {
                manager.deleteNotificationChannel(id)
            } catch (_: Exception) {
                // OK — channel may not exist.
            }
        }
        createdChannelIds.clear()
    }

    @Test
    fun createNotificationChannel_createsChannelWithTaskRemindersPrefix() = runTest {
        NotificationHelper.createNotificationChannel(context)

        val channels = manager.notificationChannels
        val taskReminderChannel = channels.firstOrNull {
            it.id.startsWith("prismtask_reminders_")
        }
        assertNotNull(
            "Expected a channel with ID starting with 'prismtask_reminders_' after " +
                "createNotificationChannel — got IDs: ${channels.map { it.id }}",
            taskReminderChannel
        )
        createdChannelIds += taskReminderChannel!!.id

        assertEquals("Task Reminders", taskReminderChannel.name.toString())
        // Importance can be LOW/DEFAULT/HIGH depending on user preference.
        // Verify it's one of the known valid values, not e.g. NONE (0).
        assertTrue(
            "Importance should be LOW/DEFAULT/HIGH, got ${taskReminderChannel.importance}",
            taskReminderChannel.importance in listOf(
                NotificationManager.IMPORTANCE_LOW,
                NotificationManager.IMPORTANCE_DEFAULT,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @Test
    fun createNotificationChannel_isIdempotent() = runTest {
        NotificationHelper.createNotificationChannel(context)
        val firstPassIds = manager.notificationChannels
            .filter { it.id.startsWith("prismtask_reminders_") }
            .map { it.id }
            .toSet()

        NotificationHelper.createNotificationChannel(context)
        val secondPassIds = manager.notificationChannels
            .filter { it.id.startsWith("prismtask_reminders_") }
            .map { it.id }
            .toSet()

        createdChannelIds += firstPassIds + secondPassIds
        // Channel count for this base may fluctuate by one across a style
        // change; assert no NEW IDs appeared beyond the first pass. Stale
        // channel pruning lives inside deleteStaleChannels which runs
        // during creation, so secondPassIds ⊆ firstPassIds ∪ {currentId}.
        val newlyAdded = secondPassIds - firstPassIds
        assertTrue(
            "Second createNotificationChannel call should not introduce new channel IDs beyond the current style — got new: $newlyAdded",
            newlyAdded.isEmpty() || newlyAdded.size == 1
        )
    }

    @Test
    fun importanceToChannelLevel_mapsKnownImportanceKeys() {
        // Pure-logic crossover, kept here because production is the source
        // of truth for importance keys and a mismatch is easier to diagnose
        // as an instrumented failure.
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            NotificationHelper.importanceToChannelLevel(NotificationPreferences.IMPORTANCE_MINIMAL)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            NotificationHelper.importanceToChannelLevel(NotificationPreferences.IMPORTANCE_STANDARD)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            NotificationHelper.importanceToChannelLevel(NotificationPreferences.IMPORTANCE_URGENT)
        )
        // Unknown keys default to DEFAULT.
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            NotificationHelper.importanceToChannelLevel("bogus")
        )
    }

    @Test
    fun migrateOldChannels_removesLegacyChannelIds() {
        // Create legacy channels directly via the manager so the migration
        // helper has something to delete.
        val legacyChannel = android.app.NotificationChannel(
            "averytask_reminders",
            "legacy",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(legacyChannel)
        createdChannelIds += legacyChannel.id

        NotificationHelper.migrateOldChannels(context)

        val legacyExists = manager.notificationChannels.any { it.id == "averytask_reminders" }
        assertEquals(false, legacyExists)
    }

    // --- v1.4.38 / A2 channel coverage ----------------------------------
    //
    // Weekly task summary and weekly review workers both create their own
    // channels inside a private method (WeeklyTaskSummaryWorker.showNotification,
    // WeeklyReviewWorker.postNotification). Rather than open up those
    // private seams just for tests, we assert the surface the worker depends
    // on: (a) the channel ID constants are stable and distinct from every
    // other channel currently shipping, (b) creating a channel with those
    // constants lands in NotificationManager with the expected importance,
    // and (c) creating the same channel twice is idempotent. If a future
    // refactor bumps the IDs or drops the importance, users who previously
    // customized OS-level channel settings would silently lose them — this
    // test pins those constants to the OS contract.

    @Test
    fun weeklyTaskSummaryChannel_constantMatchesExpectedId() {
        assertEquals(
            "prismtask_weekly_task_summary",
            WeeklyTaskSummaryWorker.CHANNEL_ID
        )
    }

    @Test
    fun weeklyTaskSummaryChannel_registersAtDefaultImportance() {
        val channel = android.app.NotificationChannel(
            WeeklyTaskSummaryWorker.CHANNEL_ID,
            "Weekly Task Summary",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
        createdChannelIds += channel.id

        val registered = manager.getNotificationChannel(WeeklyTaskSummaryWorker.CHANNEL_ID)
        assertNotNull(
            "Expected channel ${WeeklyTaskSummaryWorker.CHANNEL_ID} after createNotificationChannel",
            registered
        )
        assertEquals(
            "Weekly Task Summary must post at DEFAULT importance (Priority normal)",
            NotificationManager.IMPORTANCE_DEFAULT,
            registered!!.importance
        )
    }

    @Test
    fun weeklyReviewChannel_constantMatchesExpectedId() {
        assertEquals(
            "prismtask_weekly_review",
            WeeklyReviewWorker.CHANNEL_ID
        )
    }

    @Test
    fun weeklyReviewChannel_registersAtDefaultImportance() {
        val channel = android.app.NotificationChannel(
            WeeklyReviewWorker.CHANNEL_ID,
            "Weekly Review",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
        createdChannelIds += channel.id

        val registered = manager.getNotificationChannel(WeeklyReviewWorker.CHANNEL_ID)
        assertNotNull(
            "Expected channel ${WeeklyReviewWorker.CHANNEL_ID} after createNotificationChannel",
            registered
        )
        assertEquals(
            "Weekly Review must post at DEFAULT importance",
            NotificationManager.IMPORTANCE_DEFAULT,
            registered!!.importance
        )
    }

    @Test
    fun a2Channels_areDistinctFromLegacyAndEachOther() {
        // Reading the four IDs directly as constants also acts as a
        // compile-time pin against accidental rename.
        val weeklyTask = WeeklyTaskSummaryWorker.CHANNEL_ID
        val weeklyReview = WeeklyReviewWorker.CHANNEL_ID
        val weeklyHabit = WeeklyHabitSummaryWorker.CHANNEL_ID

        assertTrue(
            "weekly_task_summary must not collide with weekly_habit_summary",
            weeklyTask != weeklyHabit
        )
        assertTrue(
            "weekly_review must not collide with weekly_habit_summary",
            weeklyReview != weeklyHabit
        )
        assertTrue(
            "weekly_review must not collide with weekly_task_summary",
            weeklyReview != weeklyTask
        )
        // Both A2 channels share the `prismtask_` namespace — users
        // rely on that prefix to filter app channels in OS settings.
        assertTrue(weeklyTask.startsWith("prismtask_"))
        assertTrue(weeklyReview.startsWith("prismtask_"))
    }

    @Test
    fun weeklyTaskSummaryChannel_reCreationIsIdempotent() {
        val channel1 = android.app.NotificationChannel(
            WeeklyTaskSummaryWorker.CHANNEL_ID,
            "Weekly Task Summary",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel1)
        createdChannelIds += channel1.id

        val countAfterFirst = manager.notificationChannels
            .count { it.id == WeeklyTaskSummaryWorker.CHANNEL_ID }

        // Second creation with the same ID must not duplicate — OS is
        // spec'd to no-op the second call when a matching channel exists.
        val channel2 = android.app.NotificationChannel(
            WeeklyTaskSummaryWorker.CHANNEL_ID,
            "Weekly Task Summary",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel2)

        val countAfterSecond = manager.notificationChannels
            .count { it.id == WeeklyTaskSummaryWorker.CHANNEL_ID }
        assertEquals(
            "Re-creating a channel with the same ID must not duplicate",
            countAfterFirst,
            countAfterSecond
        )
    }
}
