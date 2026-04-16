package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.averycorp.prismtask.data.calendar.CalendarManager
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.CalendarSyncRepository
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val notificationPreferences: NotificationPreferences,
    private val calendarManager: CalendarManager,
    private val calendarSyncRepository: CalendarSyncRepository
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

    /**
     * Determines whether a habit nag notification should be suppressed
     * because the habit has a scheduled occurrence within the suppression
     * window.
     *
     * Returns the [LocalDateTime] at which a delayed follow-up should
     * fire, or null if the nag should proceed normally.
     */
    suspend fun getFollowUpTimeIfSuppressed(habit: HabitEntity): LocalDateTime? {
        val globalDays = notificationPreferences.getHabitNagSuppressionDaysOnce()
        val suppressionDays = HabitNotificationUtils.resolveSuppressionDays(habit, globalDays)
        if (suppressionDays == 0) return null

        val now = LocalDate.now()
        val windowEnd = now.plusDays(suppressionDays.toLong())

        // Check 1: habit has a fixed reminder time (millis from midnight)
        if (habit.reminderTime != null) {
            val reminderHour = (habit.reminderTime / (60 * 60 * 1000)).toInt()
            val reminderMinute = ((habit.reminderTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
            // The next occurrence is today (if not passed) or tomorrow
            val todayReminder = now.atTime(reminderHour, reminderMinute)
            val nextOccurrence = if (todayReminder.isAfter(LocalDateTime.now())) {
                todayReminder
            } else {
                now.plusDays(1).atTime(reminderHour, reminderMinute)
            }
            if (!nextOccurrence.toLocalDate().isAfter(windowEnd)) {
                // Schedule follow-up 1 hour after the scheduled time
                return nextOccurrence.plusHours(1)
            }
        }

        // Check 2: habit is linked to a calendar event within the window
        // (fuzzy match by habit name since no direct FK exists). The
        // lookup now goes through the backend so the Android client
        // doesn't need the Calendar OAuth scope; `calendarManager` is
        // still consulted to short-circuit when the user hasn't
        // connected Calendar at all.
        if (calendarManager.isCalendarConnected.value) {
            val events = getCalendarEventsForHabit(habit, now, windowEnd)
            if (events.isNotEmpty()) {
                val earliestStart = events.minOf { it }
                return earliestStart.plusHours(1)
            }
        }

        return null
    }

    /**
     * Queries Google Calendar events whose title contains the habit name
     * (case-insensitive) within the given date range.
     *
     * Returns start times of matching events. If calendar is not connected
     * or the query fails, returns an empty list.
     *
     * Known limitation: this is a fuzzy title-match fallback since habits
     * are not directly linked to calendar events via a foreign key.
     */
    private suspend fun getCalendarEventsForHabit(
        habit: HabitEntity,
        from: LocalDate,
        to: LocalDate
    ): List<LocalDateTime> {
        val zone = ZoneId.systemDefault()
        val timeMin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val timeMax = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = calendarSyncRepository.searchEventsBySummary(
            pattern = habit.name,
            timeMinMillis = timeMin,
            timeMaxMillis = timeMax
        )
        return events
            .filter { it.summary.contains(habit.name, ignoreCase = true) }
            .map { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDateTime() }
    }

    /**
     * Schedules a delayed follow-up notification for a habit at the given
     * time. Cancels any existing pending nag for this habit first.
     */
    fun scheduleDelayedHabitFollowUp(habitId: Long, habitName: String, fireAt: LocalDateTime) {
        // Cancel any existing pending nag first
        cancel(habitId)
        cancelFollowUp(habitId)

        val zone = ZoneId.systemDefault()
        val triggerTime = fireAt.atZone(zone).toInstant().toEpochMilli()

        val intent = Intent(context, HabitFollowUpReceiver::class.java).apply {
            putExtra(HabitFollowUpReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(HabitFollowUpReceiver.EXTRA_HABIT_NAME, habitName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.toInt() + HabitFollowUpReceiver.FOLLOW_UP_REQUEST_CODE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ExactAlarmHelper.scheduleExact(context, triggerTime, pendingIntent)
    }

    /**
     * Cancels any pending follow-up notification for the given habit.
     * Called when: the habit is completed, deleted, or archived.
     */
    fun cancelFollowUp(habitId: Long) {
        HabitFollowUpDismissReceiver.cancelFollowUp(context, habitId)
        // Also dismiss the notification if it's already showing
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(HabitFollowUpReceiver.followUpNotificationId(habitId))
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
