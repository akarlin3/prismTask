package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.TaskDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler
@Inject
constructor(
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

        ExactAlarmHelper.scheduleExact(context, triggerTime, pendingIntent)
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
            val effective = combineDateAndTime(dueDate, task.dueTime)
            scheduleReminder(task.id, task.title, task.description, effective, offset)
        }
    }

    companion object {
        /**
         * Pure helper: compute the wall-clock time at which a reminder should
         * fire given a task's due date and how far in advance the user wants
         * to be nudged. Returned timestamp may be in the past — callers should
         * use [isInFuture] to decide whether to actually schedule an alarm.
         */
        fun computeTriggerTime(dueDate: Long, reminderOffset: Long): Long =
            dueDate - reminderOffset

        /**
         * Pure helper: the alarm should only be registered when the computed
         * trigger time is strictly in the future. Mirrors the guard clause in
         * [scheduleReminder].
         */
        fun isInFuture(triggerTime: Long, now: Long): Boolean = triggerTime > now

        /**
         * Combine a task's stored [dueDate] (midnight of the due day) with
         * its optional [dueTime] (a timestamp whose HH:mm:ss.SSS is the
         * user-selected time-of-day) into a single absolute instant. When
         * [dueTime] is null the caller has not chosen a specific time, so
         * the raw [dueDate] is returned unchanged.
         *
         * Reminders previously passed just [dueDate] to [scheduleReminder],
         * so a 10-minute reminder on a task due today at 3pm would compute
         * a trigger time of 11:50pm *yesterday* — in the past, silently
         * dropped by the `triggerTime <= now` guard. This helper lets
         * callers pass the correctly combined instant so the alarm actually
         * fires at the expected time.
         */
        fun combineDateAndTime(dueDate: Long, dueTime: Long?): Long {
            if (dueTime == null) return dueDate
            val timeOfDay = Calendar.getInstance().apply { timeInMillis = dueTime }
            return Calendar.getInstance().apply {
                timeInMillis = dueDate
                set(Calendar.HOUR_OF_DAY, timeOfDay.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, timeOfDay.get(Calendar.MINUTE))
                set(Calendar.SECOND, timeOfDay.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, timeOfDay.get(Calendar.MILLISECOND))
            }.timeInMillis
        }
    }
}
