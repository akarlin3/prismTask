package com.averycorp.prismtask.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.repository.TaskRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.GlobalScope
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

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(taskId.toInt())

        @Suppress("GlobalCoroutineUsage")
        GlobalScope.launch {
            repository.completeTask(taskId)
        }
    }
}
