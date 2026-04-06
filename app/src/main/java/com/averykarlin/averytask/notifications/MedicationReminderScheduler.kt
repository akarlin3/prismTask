package com.averykarlin.averytask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext(
        habitId: Long,
        habitName: String,
        habitDescription: String?,
        completedAt: Long,
        intervalMillis: Long,
        doseNumber: Int = 0,
        totalDoses: Int = 1
    ) {
        val triggerTime = maxOf(completedAt + intervalMillis, System.currentTimeMillis() + 1000)

        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("habitId", habitId)
            putExtra("habitName", habitName)
            putExtra("habitDescription", habitDescription)
            putExtra("intervalMillis", intervalMillis)
            putExtra("doseNumber", doseNumber)
            putExtra("totalDoses", totalDoses)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.toInt() + 200_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    fun cancel(habitId: Long) {
        val intent = Intent(context, MedicationReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.toInt() + 200_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    suspend fun rescheduleAll() {
        val habits = habitDao.getHabitsWithIntervalReminder()
        val today = com.averykarlin.averytask.data.repository.HabitRepository.normalizeToMidnight(System.currentTimeMillis())
        for (habit in habits) {
            val interval = habit.reminderIntervalMillis ?: continue
            val timesPerDay = habit.reminderTimesPerDay
            val todayCount = completionDao.getCompletionCountForDateOnce(habit.id, today)
            if (todayCount >= timesPerDay) continue
            val lastCompletion = completionDao.getLastCompletionOnce(habit.id) ?: continue
            scheduleNext(
                habit.id, habit.name, habit.description, lastCompletion.completedAt, interval,
                doseNumber = todayCount + 1, totalDoses = timesPerDay
            )
        }
    }
}
