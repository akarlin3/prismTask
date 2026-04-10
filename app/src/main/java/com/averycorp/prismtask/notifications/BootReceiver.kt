package com.averycorp.prismtask.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun reminderScheduler(): ReminderScheduler
        fun medicationReminderScheduler(): MedicationReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootEntryPoint::class.java
        )

        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            entryPoint.reminderScheduler().rescheduleAllReminders()
            entryPoint.medicationReminderScheduler().rescheduleAll()
        }
    }
}
