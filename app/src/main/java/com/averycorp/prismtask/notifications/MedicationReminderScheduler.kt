package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao,
    private val medicationPreferences: MedicationPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
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

        scheduleExactOrFallback(triggerTime, pendingIntent)
    }

    /**
     * Schedule a reminder at a specific time of day.
     * Uses request code offset 300_000 + index to avoid collision with interval-based reminders.
     */
    fun scheduleAtSpecificTime(
        timeIndex: Int,
        triggerTimeMillis: Long,
        habitName: String
    ) {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("habitId", 0L)
            putExtra("habitName", habitName)
            putExtra("habitDescription", "Scheduled medication time")
            putExtra("intervalMillis", 0L)
            putExtra("doseNumber", timeIndex + 1)
            putExtra("totalDoses", -1) // sentinel: specific-time mode
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            300_000 + timeIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrFallback(triggerTimeMillis, pendingIntent)
    }

    private fun scheduleExactOrFallback(triggerTime: Long, pendingIntent: PendingIntent) {
        ExactAlarmHelper.scheduleExact(context, triggerTime, pendingIntent)
    }

    fun cancelSpecificTime(timeIndex: Int) {
        val intent = Intent(context, MedicationReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            300_000 + timeIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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

    /** Schedule alarms for all specific times that haven't passed yet today. */
    suspend fun scheduleSpecificTimes() {
        val times = medicationPreferences.getSpecificTimesOnce()
        val now = System.currentTimeMillis()
        val sortedTimes = times.sorted().toList()

        sortedTimes.forEachIndexed { index, timeStr ->
            val triggerMillis = timeStringToNextTrigger(timeStr, now)
            scheduleAtSpecificTime(index, triggerMillis, "Medication Reminder")
        }
        // Cancel any leftover slots beyond current count
        for (i in sortedTimes.size until sortedTimes.size + 10) {
            cancelSpecificTime(i)
        }
    }

    suspend fun rescheduleAll() {
        val mode = medicationPreferences.getScheduleModeOnce()
        if (mode == MedicationScheduleMode.SPECIFIC_TIMES) {
            scheduleSpecificTimes()
            return
        }

        val habits = habitDao.getHabitsWithIntervalReminder()
        val today = DayBoundary.startOfCurrentDay(taskBehaviorPreferences.getDayStartHour().first())
        for (habit in habits) {
            val interval = habit.reminderIntervalMillis ?: continue
            val timesPerDay = habit.reminderTimesPerDay
            val todayCount = completionDao.getCompletionCountForDateOnce(habit.id, today)
            if (todayCount >= timesPerDay) continue
            val lastCompletion = completionDao.getLastCompletionOnce(habit.id) ?: continue
            scheduleNext(
                habit.id,
                habit.name,
                habit.description,
                lastCompletion.completedAt,
                interval,
                doseNumber = todayCount + 1,
                totalDoses = timesPerDay
            )
        }
    }

    companion object {
        /** Convert "HH:mm" to next trigger timestamp (today if not passed, tomorrow if passed). */
        fun timeStringToNextTrigger(timeStr: String, now: Long): Long {
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
    }
}
