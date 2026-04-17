package com.averycorp.prismtask.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.averycorp.prismtask.data.repository.HabitRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        context.getSystemService(NotificationManager::class.java)?.cancel(habitId.toInt() + 200_000)

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                repository.completeHabit(habitId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("LogMedicationReceiver", "Failed to log habit $habitId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
