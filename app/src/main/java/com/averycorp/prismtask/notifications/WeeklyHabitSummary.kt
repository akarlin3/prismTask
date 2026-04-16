package com.averycorp.prismtask.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.toCalendarDayOfWeek
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class WeeklySummaryData(
    val totalHabits: Int,
    val totalCompletions: Int,
    val completionRate: Float,
    val bestHabit: String?,
    val worstHabit: String?
)

@Singleton
class WeeklyHabitSummary
@Inject
constructor(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
) {
    companion object {
        private const val CHANNEL_ID = "prismtask_weekly_summary"
        private const val CHANNEL_NAME = "Weekly Summary"
        private const val LEGACY_CHANNEL_ID = "averytask_weekly_summary"
        private const val NOTIFICATION_ID = 9999
    }

    suspend fun generateWeeklySummary(): WeeklySummaryData {
        val habits = habitDao.getActiveHabitsOnce()
        val calendarDow = taskBehaviorPreferences.getFirstDayOfWeek().first().toCalendarDayOfWeek()
        val weekStart = HabitRepository.getWeekStart(
            HabitRepository.normalizeToMidnight(System.currentTimeMillis()),
            calendarDow
        )
        val weekEnd = HabitRepository.getWeekEnd(
            HabitRepository.normalizeToMidnight(System.currentTimeMillis()),
            calendarDow
        )

        var totalCompletions = 0
        var bestName: String? = null
        var bestCount = -1
        var worstName: String? = null
        var worstCount = Int.MAX_VALUE

        for (habit in habits) {
            val completions = completionDao.getCompletionsForHabitOnce(habit.id)
            val weekCount = completions.count { it.completedDate in weekStart..weekEnd }
            totalCompletions += weekCount

            if (weekCount > bestCount) {
                bestCount = weekCount
                bestName = habit.name
            }
            if (weekCount < worstCount) {
                worstCount = weekCount
                worstName = habit.name
            }
        }

        val totalPossible = habits.sumOf { it.targetFrequency * 7 }
        val rate = if (totalPossible > 0) totalCompletions.toFloat() / totalPossible else 0f

        return WeeklySummaryData(
            totalHabits = habits.size,
            totalCompletions = totalCompletions,
            completionRate = rate,
            bestHabit = bestName,
            worstHabit = worstName
        )
    }

    fun showWeeklyNotification(context: Context, data: WeeklySummaryData) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Weekly habit summary" }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(channel)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildString {
            append("This week you finished ${data.totalCompletions} things.")
            data.bestHabit?.let { append(" Here's what went well: $it.") }
        }

        val notification = NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Your Week in Review")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
