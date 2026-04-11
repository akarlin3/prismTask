package com.averycorp.prismtask.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetTaskBehaviorPrefs by preferencesDataStore(name = "task_behavior_prefs")
private val DAY_START_HOUR_KEY = intPreferencesKey("day_start_hour")

private suspend fun Context.readDayStartHour(): Int =
    widgetTaskBehaviorPrefs.data.map { it[DAY_START_HOUR_KEY] ?: 0 }.first()

/**
 * Lightweight task row used by widgets — includes only the fields the
 * Glance UI actually renders. Avoids flowing the full TaskEntity into a
 * composable (which would cost far more in Glance serialisation).
 */
data class WidgetTaskRow(
    val id: Long,
    val title: String,
    val priority: Int,
    val dueDate: Long?,
    val isCompleted: Boolean,
    val isOverdue: Boolean
)

data class TodayWidgetData(
    val totalTasks: Int,
    val completedTasks: Int,
    val tasks: List<WidgetTaskRow>,
    val totalHabits: Int,
    val completedHabits: Int,
    val habitIcons: List<String>,
    val productivityScore: Int
)

data class HabitWidgetData(
    val habits: List<HabitWidgetItem>,
    val longestStreak: Int
)

data class HabitWidgetItem(
    val id: Long,
    val name: String,
    val icon: String,
    val streak: Int,
    val isCompletedToday: Boolean
)

/** Upcoming widget: grouped 3-day preview. */
data class UpcomingWidgetData(
    val overdue: List<WidgetTaskRow>,
    val today: List<WidgetTaskRow>,
    val tomorrow: List<WidgetTaskRow>,
    val dayAfter: List<WidgetTaskRow>
) {
    val totalCount: Int get() = overdue.size + today.size + tomorrow.size + dayAfter.size
}

/** Productivity score widget data. */
data class ProductivityWidgetData(
    val score: Int,
    val completed: Int,
    val total: Int,
    val trendPoints: Int
)

/** Template shortcut used by the Quick-Add widget's template tiles. */
data class TemplateShortcut(
    val id: Long,
    val name: String,
    val icon: String
)

object WidgetDataProvider {

    private fun getDb(context: Context): PrismTaskDatabase =
        Room.databaseBuilder(context, PrismTaskDatabase::class.java, "averytask.db")
            .addMigrations(*com.averycorp.prismtask.data.local.database.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .build()

    private fun TaskEntity.toRow(startOfDay: Long): WidgetTaskRow = WidgetTaskRow(
        id = id,
        title = title,
        priority = priority,
        dueDate = dueDate,
        isCompleted = isCompleted,
        isOverdue = !isCompleted && (dueDate ?: Long.MAX_VALUE) < startOfDay
    )

    suspend fun getTodayData(context: Context): TodayWidgetData {
        val db = getDb(context)
        try {
            val dayStartHour = context.readDayStartHour()
            val startOfDay = DayBoundary.startOfCurrentDay(dayStartHour)
            val endOfDay = startOfDay + DayBoundary.DAY_MILLIS

            val taskDao = db.taskDao()
            val todayTasks = taskDao.getTodayTasksOnce(startOfDay, endOfDay)
            val overdueTasks = taskDao.getOverdueRootTasksOnce(startOfDay)
            val allTasks = overdueTasks + todayTasks
            val completedToday = taskDao.getCompletedTodayOnce(startOfDay)

            val habitDao = db.habitDao()
            val habits = habitDao.getActiveHabitsOnce()
            val completionDao = db.habitCompletionDao()
            val completedHabits = habits.count { completionDao.isCompletedOnDateOnce(it.id, startOfDay) }

            val totalForScore = allTasks.size + completedToday.size + habits.size
            val completedForScore = completedToday.size + completedHabits
            val productivityScore = if (totalForScore > 0) {
                ((completedForScore * 100f) / totalForScore).toInt().coerceIn(0, 100)
            } else 0

            return TodayWidgetData(
                totalTasks = allTasks.size + completedToday.size,
                completedTasks = completedToday.size,
                tasks = (completedToday + allTasks).take(8).map { it.toRow(startOfDay) },
                totalHabits = habits.size,
                completedHabits = completedHabits,
                habitIcons = habits.take(6).map { it.icon },
                productivityScore = productivityScore
            )
        } finally {
            db.close()
        }
    }

    suspend fun getHabitData(context: Context): HabitWidgetData {
        val db = getDb(context)
        try {
            val habitDao = db.habitDao()
            val completionDao = db.habitCompletionDao()
            val habits = habitDao.getActiveHabitsOnce()
            val dayStartHour = context.readDayStartHour()
            val startOfDay = DayBoundary.startOfCurrentDay(dayStartHour)

            var longestStreak = 0
            val items = habits.take(12).map { habit ->
                val isCompleted = completionDao.isCompletedOnDateOnce(habit.id, startOfDay)
                val streak = computeCurrentStreak(
                    completionDao = completionDao,
                    habitId = habit.id,
                    startOfDay = startOfDay
                )
                if (streak > longestStreak) longestStreak = streak
                HabitWidgetItem(
                    id = habit.id,
                    name = habit.name,
                    icon = habit.icon,
                    streak = streak,
                    isCompletedToday = isCompleted
                )
            }

            return HabitWidgetData(habits = items, longestStreak = longestStreak)
        } finally {
            db.close()
        }
    }

    private suspend fun computeCurrentStreak(
        completionDao: com.averycorp.prismtask.data.local.dao.HabitCompletionDao,
        habitId: Long,
        startOfDay: Long
    ): Int {
        var streak = 0
        var date = startOfDay
        // Walk backwards from today (or yesterday if today isn't done).
        val todayDone = completionDao.isCompletedOnDateOnce(habitId, date)
        if (!todayDone) date -= DayBoundary.DAY_MILLIS
        while (completionDao.isCompletedOnDateOnce(habitId, date)) {
            streak++
            date -= DayBoundary.DAY_MILLIS
            if (streak > 365) break
        }
        return streak
    }

    suspend fun getUpcomingData(context: Context): UpcomingWidgetData {
        val db = getDb(context)
        try {
            val dayStartHour = context.readDayStartHour()
            val startOfDay = DayBoundary.startOfCurrentDay(dayStartHour)
            val endOfDay = startOfDay + DayBoundary.DAY_MILLIS
            val tomorrowStart = endOfDay
            val tomorrowEnd = tomorrowStart + DayBoundary.DAY_MILLIS
            val dayAfterStart = tomorrowEnd
            val dayAfterEnd = dayAfterStart + DayBoundary.DAY_MILLIS

            val taskDao = db.taskDao()
            val overdue = taskDao.getOverdueRootTasksOnce(startOfDay)
            val today = taskDao.getTodayTasksOnce(startOfDay, endOfDay)
            val tomorrow = taskDao.getTodayTasksOnce(tomorrowStart, tomorrowEnd)
            val dayAfter = taskDao.getTodayTasksOnce(dayAfterStart, dayAfterEnd)

            return UpcomingWidgetData(
                overdue = overdue.take(3).map { it.toRow(startOfDay) },
                today = today.take(5).map { it.toRow(startOfDay) },
                tomorrow = tomorrow.take(5).map { it.toRow(startOfDay) },
                dayAfter = dayAfter.take(5).map { it.toRow(startOfDay) }
            )
        } finally {
            db.close()
        }
    }

    suspend fun getProductivityData(context: Context): ProductivityWidgetData {
        val db = getDb(context)
        try {
            val dayStartHour = context.readDayStartHour()
            val startOfDay = DayBoundary.startOfCurrentDay(dayStartHour)
            val endOfDay = startOfDay + DayBoundary.DAY_MILLIS

            val taskDao = db.taskDao()
            val todayTasks = taskDao.getTodayTasksOnce(startOfDay, endOfDay)
            val overdue = taskDao.getOverdueRootTasksOnce(startOfDay)
            val completed = taskDao.getCompletedTodayOnce(startOfDay)
            val totalTasks = todayTasks.size + overdue.size + completed.size
            val completedTasks = completed.size

            val habitDao = db.habitDao()
            val completionDao = db.habitCompletionDao()
            val habits = habitDao.getActiveHabitsOnce()
            val completedHabits = habits.count {
                completionDao.isCompletedOnDateOnce(it.id, startOfDay)
            }

            val total = totalTasks + habits.size
            val done = completedTasks + completedHabits
            val score = if (total > 0) {
                ((done * 100f) / total).toInt().coerceIn(0, 100)
            } else 0

            // Trend: previous day comparison. If we don't have enough data,
            // the trend is reported as 0.
            val yesterdayStart = startOfDay - DayBoundary.DAY_MILLIS
            val prevCompleted = taskDao.getCompletedTodayOnce(yesterdayStart)
                .count { (it.completedAt ?: 0) < startOfDay }
            val prevScore = if (total > 0) ((prevCompleted * 100f) / total).toInt() else 0
            val trend = score - prevScore

            return ProductivityWidgetData(
                score = score,
                completed = done,
                total = total,
                trendPoints = trend
            )
        } finally {
            db.close()
        }
    }

    suspend fun getTopTemplates(context: Context, limit: Int = 3): List<TemplateShortcut> {
        val db = getDb(context)
        try {
            val templates = db.taskTemplateDao().getAllTemplatesOnce()
            return templates.take(limit).map {
                TemplateShortcut(
                    id = it.id,
                    name = it.name,
                    icon = it.icon ?: "\uD83D\uDCCB"
                )
            }
        } finally {
            db.close()
        }
    }

    suspend fun toggleTaskCompletion(context: Context, taskId: Long) {
        val db = getDb(context)
        try {
            val taskDao = db.taskDao()
            val task = taskDao.getTaskByIdOnce(taskId) ?: return
            val now = System.currentTimeMillis()
            if (task.isCompleted) {
                taskDao.markIncomplete(taskId, now)
            } else {
                taskDao.markCompleted(taskId, now)
            }
        } finally {
            db.close()
        }
    }

    suspend fun toggleHabitCompletion(context: Context, habitId: Long) {
        val db = getDb(context)
        try {
            val dayStartHour = context.readDayStartHour()
            val startOfDay = DayBoundary.startOfCurrentDay(dayStartHour)
            val completionDao = db.habitCompletionDao()
            val isDone = completionDao.isCompletedOnDateOnce(habitId, startOfDay)
            if (isDone) {
                completionDao.deleteByHabitAndDate(habitId, startOfDay)
            } else {
                completionDao.insert(
                    com.averycorp.prismtask.data.local.entity.HabitCompletionEntity(
                        habitId = habitId,
                        completedDate = startOfDay,
                        completedAt = System.currentTimeMillis()
                    )
                )
            }
        } finally {
            db.close()
        }
    }
}
