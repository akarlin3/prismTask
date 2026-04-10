package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.TaskDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleReminder(
        taskId: Long,
        taskTitle: String,
        taskDescription: String?,
        dueDate: Long,
        reminderOffset: Long
    ) {
        val triggerTime = dueDate - reminderOffset
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("taskTitle", taskTitle)
            putExtra("taskDescription", taskDescription)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    fun cancelReminder(taskId: Long) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    suspend fun rescheduleAllReminders() {
        val tasks = taskDao.getIncompleteTasksWithReminders()
        for (task in tasks) {
            val dueDate = task.dueDate ?: continue
            val offset = task.reminderOffset ?: continue
            scheduleReminder(task.id, task.title, task.description, dueDate, offset)
        }
    }
}
