package com.averycorp.prismtask.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.averycorp.prismtask.data.repository.TaskRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CompleteTaskReceiver : BroadcastReceiver() {
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface CompleteTaskEntryPoint {
        fun taskRepository(): TaskRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1L)
        if (taskId == -1L) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            CompleteTaskEntryPoint::class.java
        )
        val repository = entryPoint.taskRepository()

        context.getSystemService(NotificationManager::class.java)?.cancel(taskId.toInt())

        // Extend the receiver's lifetime until completeTask() finishes so the
        // coroutine isn't killed when Android tears down the receiver process.
        // Using an app-scoped SupervisorJob + goAsync() is the recommended
        // pattern for async work from a BroadcastReceiver.
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                repository.completeTask(taskId)
            } catch (e: Exception) {
                Log.e("CompleteTaskReceiver", "Failed to complete task $taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
