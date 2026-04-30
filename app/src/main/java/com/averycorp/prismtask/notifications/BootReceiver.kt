package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun reminderScheduler(): ReminderScheduler

        fun habitReminderScheduler(): HabitReminderScheduler

        fun medicationReminderScheduler(): MedicationReminderScheduler

        fun medicationClockRescheduler(): MedicationClockRescheduler

        fun medicationIntervalRescheduler(): MedicationIntervalRescheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                BootEntryPoint::class.java
            )
        } catch (e: IllegalStateException) {
            // Hilt's application component was not ready when this receiver
            // fired. In production this can happen on some OEM builds that
            // replay BOOT_COMPLETED before Application.onCreate finishes, and
            // in instrumented tests that run under HiltTestApplication without
            // a HiltAndroidRule (the component is created lazily by the
            // rule). A missed boot-time reschedule is harmless — alarms
            // re-register on the next app launch — so log and drop rather
            // than crash the host process.
            Log.w("BootReceiver", "Hilt EntryPoints unavailable on boot; skipping reschedule", e)
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                entryPoint.reminderScheduler().rescheduleAllReminders()
                entryPoint.habitReminderScheduler().rescheduleAll()
                entryPoint.medicationReminderScheduler().rescheduleAll()
                entryPoint.medicationClockRescheduler().rescheduleAll()
                entryPoint.medicationIntervalRescheduler().rescheduleAll()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule reminders on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
