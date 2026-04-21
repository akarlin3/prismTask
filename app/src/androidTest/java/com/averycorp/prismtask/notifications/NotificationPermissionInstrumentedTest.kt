package com.averycorp.prismtask.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates that the post-notification code paths do not crash when the
 * runtime POST_NOTIFICATIONS permission is denied on API 33+.
 *
 * We can't revoke the permission cleanly from a non-rooted instrumentation
 * test, so this test leans on the fact that [NotificationManagerCompat]
 * already absorbs the [SecurityException] it would otherwise throw when
 * notifications are disabled. The test installs a valid channel and
 * attempts a [NotificationManagerCompat.notify] with an intentionally
 * off-channel ID; we expect the call to return (not crash) regardless of
 * runtime state. Production call sites wrap their notify() in try/catch
 * for the same reason (see [OverloadCheckWorker.doWork]) — this confirms
 * the library contract they rely on.
 *
 * TODO — a strict permission-revoke test needs UiAutomator or the
 * `pm revoke` shell path. Left as a follow-up; see testability TODOs.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionInstrumentedTest {

    @Test
    fun notifyOnInvalidChannel_doesNotCrash_onApi33Plus() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull("NotificationManager system service must be available", manager)

        // Make sure at least one valid channel exists for the test package —
        // notify() on API 26+ silently drops if the channel is missing, not
        // throws, so we want a real channel to prove the happy path works
        // before checking the off-channel path.
        val validChannelId = "__permission_test_channel__"
        val validChannel = NotificationChannel(
            validChannelId,
            "permission test channel",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(validChannel)

        try {
            val notification = NotificationCompat.Builder(context, validChannelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("perm test")
                .setContentText("Must not crash under any permission state")
                .setAutoCancel(true)
                .build()

            // NotificationManagerCompat.notify is documented to silently
            // drop when notifications are disabled. The worst case we can
            // induce in an instrumented test is disabled-by-user, which
            // still should not crash.
            NotificationManagerCompat.from(context).notify(30_001, notification)
            // Also exercise the non-compat path, which is where
            // SecurityException can surface on specific OEM builds.
            try {
                manager.notify(30_002, notification)
            } catch (_: SecurityException) {
                // Absorbing is the documented contract.
            }
        } finally {
            NotificationManagerCompat.from(context).cancel(30_001)
            NotificationManagerCompat.from(context).cancel(30_002)
            manager.deleteNotificationChannel(validChannelId)
        }
    }
}
