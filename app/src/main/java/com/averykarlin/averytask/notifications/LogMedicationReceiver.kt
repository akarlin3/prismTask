package com.averykarlin.averytask.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.averykarlin.averytask.data.repository.HabitRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LogMedicationReceiver : BroadcastReceiver() {

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface LogMedicationEntryPoint {
        fun habitRepository(): HabitRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra("habitId", -1L)
        if (habitId == -1L) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            LogMedicationEntryPoint::class.java
        )
        val repository = entryPoint.habitRepository()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(habitId.toInt() + 200_000)

        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            repository.completeHabit(habitId, System.currentTimeMillis())
        }
    }
}
