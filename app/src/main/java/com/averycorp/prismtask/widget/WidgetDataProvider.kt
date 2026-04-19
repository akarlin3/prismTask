package com.averycorp.prismtask.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.preferences.taskBehaviorDataStore
import com.averycorp.prismtask.data.preferences.themePrefsDataStore
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val DAY_START_HOUR_KEY = intPreferencesKey("day_start_hour")
private val DAY_START_MINUTE_KEY = intPreferencesKey("day_start_minute")
private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

private suspend fun Context.readDayStartHour(): Int =
    taskBehaviorDataStore.data.map { it[DAY_START_HOUR_KEY] ?: 0 }.first()

private suspend fun Context.readDayStartMinute(): Int =
    taskBehaviorDataStore.data.map { it[DAY_START_MINUTE_KEY] ?: 0 }.first()

/** Reads the user's configured accent color hex for use in widgets. */
suspend fun Context.readAccentColor(): String =
    themePrefsDataStore.data.map { it[ACCENT_COLOR_KEY] ?: "#2563EB" }.first()

/** Reads the user's theme mode preference (system/light/dark). */
suspend fun Context.readThemeMode(): String =
    themePrefsDataStore.data.map { it[THEME_MODE_KEY] ?: "system" }.first()

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
    val isCompletedToday: Boolean,
    val last7Days: List<Boolean> = emptyList()
)

data class UpcomingWidgetData(
    val overdue: List<WidgetTaskRow>,
    val today: List<WidgetTaskRow>,
    val tomorrow: List<WidgetTaskRow>,
    val dayAfter: List<WidgetTaskRow>
) {
    val totalCount: Int get() = overdue.size + today.size + tomorrow.size + dayAfter.size
}

data class ProductivityWidgetData(
    val score: Int,
    val completed: Int,
    val total: Int,
    val trendPoints: Int
)

data class TemplateShortcut(
    val id: Long,
    val name: String,
    val icon: String
)

/**
 * Snapshot of a single project for the [ProjectWidget]. `nextDueTaskTitle`
 * is only populated when the project has no upcoming milestones — the
 * widget falls back to it per Phase 3 spec.
 */
data class ProjectWidgetData(
    val projectId: Long,
    val name: String,
    val icon: String,
    val themeColorHex: String,
    val status: String,
    val milestoneProgress: Float,
    val completedMilestones: Int,
    val totalMilestones: Int,
    val upcomingMilestoneTitle: String?,
    val nextDueTaskTitle: String?,
    val totalTasks: Int,
    val openTasks: Int,
    val streak: Int,
    val daysSinceActivity: Int?
)

object WidgetDataProvider {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetDatabaseEntryPoint {
        fun database(): PrismTaskDatabase
    }

    // Reuse the Hilt-provided singleton DB so we share the same
    // InvalidationTracker and avoid opening a second Room instance per
    // widget refresh (which races with the app's singleton on close()
    // and can fail during migration windows).
    private fun getDb(context: Context): PrismTaskDatabase =
        EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetDatabaseEntryPoint::class.java)
            .database()

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
        val dayStartHour = context.readDayStartHour()
        val dayStartMinute = context.readDayStartMinute()
        val startOfDay = DayBoundary.startOfCurrentDay(
            dayStartHour = dayStartHour,
            dayStartMinute = dayStartMinute
        )
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
        } else {
            0
        }
        return TodayWidgetData(
            totalTasks = allTasks.size + completedToday.size,
            completedTasks = completedToday.size,
            tasks = (completedToday + allTasks).take(8).map { it.toRow(startOfDay) },
            totalHabits = habits.size,
            completedHabits = completedHabits,
            habitIcons = habits.take(6).map { it.icon },
            productivityScore = productivityScore
        )
    }

    suspend fun getHabitData(context: Context): HabitWidgetData {
        val db = getDb(context)
        val habitDao = db.habitDao()
        val completionDao = db.habitCompletionDao()
        val habits = habitDao.getActiveHabitsOnce()
        val dayStartHour = context.readDayStartHour()
        val dayStartMinute = context.readDayStartMinute()
        val startOfDay = DayBoundary.startOfCurrentDay(
            dayStartHour = dayStartHour,
            dayStartMinute = dayStartMinute
        )
        var longestStreak = 0
        val items = habits.take(12).map { habit ->
            val isCompleted = completionDao.isCompletedOnDateOnce(habit.id, startOfDay)
            val streak = computeCurrentStreak(completionDao, habit.id, startOfDay)
            if (streak > longestStreak) longestStreak = streak
            val last7Days = (6 downTo 0).map { daysAgo ->
                completionDao.isCompletedOnDateOnce(
                    habit.id,
                    startOfDay - (daysAgo * DayBoundary.DAY_MILLIS)
                )
            }
            HabitWidgetItem(habit.id, habit.name, habit.icon, streak, isCompleted, last7Days)
        }
        return HabitWidgetData(habits = items, longestStreak = longestStreak)
    }

    private suspend fun computeCurrentStreak(
        completionDao: com.averycorp.prismtask.data.local.dao.HabitCompletionDao,
        habitId: Long,
        startOfDay: Long
    ): Int {
        var streak = 0
        var date = startOfDay
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
        val dayStartHour = context.readDayStartHour()
        val dayStartMinute = context.readDayStartMinute()
        val startOfDay = DayBoundary.startOfCurrentDay(
            dayStartHour = dayStartHour,
            dayStartMinute = dayStartMinute
        )
        val endOfDay = startOfDay + DayBoundary.DAY_MILLIS
        val taskDao = db.taskDao()
        return UpcomingWidgetData(
            overdue = taskDao.getOverdueRootTasksOnce(startOfDay).take(3).map { it.toRow(startOfDay) },
            today = taskDao.getTodayTasksOnce(startOfDay, endOfDay).take(5).map { it.toRow(startOfDay) },
            tomorrow = taskDao.getTodayTasksOnce(endOfDay, endOfDay + DayBoundary.DAY_MILLIS).take(5).map { it.toRow(startOfDay) },
            dayAfter = taskDao.getTodayTasksOnce(endOfDay + DayBoundary.DAY_MILLIS, endOfDay + 2 * DayBoundary.DAY_MILLIS).take(5).map {
                it.toRow(startOfDay)
            }
        )
    }

    suspend fun getProductivityData(context: Context): ProductivityWidgetData {
        val db = getDb(context)
        val dayStartHour = context.readDayStartHour()
        val dayStartMinute = context.readDayStartMinute()
        val startOfDay = DayBoundary.startOfCurrentDay(
            dayStartHour = dayStartHour,
            dayStartMinute = dayStartMinute
        )
        val endOfDay = startOfDay + DayBoundary.DAY_MILLIS
        val taskDao = db.taskDao()
        val todayTasks = taskDao.getTodayTasksOnce(startOfDay, endOfDay)
        val overdue = taskDao.getOverdueRootTasksOnce(startOfDay)
        val completed = taskDao.getCompletedTodayOnce(startOfDay)
        val totalTasks = todayTasks.size + overdue.size + completed.size
        val habitDao = db.habitDao()
        val completionDao = db.habitCompletionDao()
        val habits = habitDao.getActiveHabitsOnce()
        val completedHabits = habits.count { completionDao.isCompletedOnDateOnce(it.id, startOfDay) }
        val total = totalTasks + habits.size
        val done = completed.size + completedHabits
        val score = if (total > 0) ((done * 100f) / total).toInt().coerceIn(0, 100) else 0
        val yesterdayStart = startOfDay - DayBoundary.DAY_MILLIS
        val prevCompleted = taskDao.getCompletedTodayOnce(yesterdayStart).count { (it.completedAt ?: 0) < startOfDay }
        val prevScore = if (total > 0) ((prevCompleted * 100f) / total).toInt() else 0
        return ProductivityWidgetData(score = score, completed = done, total = total, trendPoints = score - prevScore)
    }

    /**
     * Snapshot of the configured project for [ProjectWidget]. Returns null
     * when the project doesn't exist (e.g. user deleted it after placing
     * the widget) — the widget renders an empty state in that case.
     */
    suspend fun getProjectData(context: Context, projectId: Long): ProjectWidgetData? {
        if (projectId <= 0) return null
        val db = getDb(context)
        val project = db.projectDao().getProjectByIdOnce(projectId) ?: return null
        val aggregate = db.projectDao().getAggregateRow(projectId)
        val milestoneTimestamps = db.milestoneDao().getCompletedTimestamps(projectId)
        val taskDates = db.projectDao().getTaskActivityDates(projectId)

        val activityDates = (taskDates + milestoneTimestamps)
            .map { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            .toSet()
        val streak = com.averycorp.prismtask.domain.usecase.DailyForgivenessStreakCore
            .calculate(activityDates).resilientStreak

        val lastActivity = (taskDates + milestoneTimestamps).maxOrNull()
        val daysSince = lastActivity?.let {
            val diff = System.currentTimeMillis() - it
            if (diff <= 0) 0 else (diff / DayBoundary.DAY_MILLIS).toInt()
        }

        // Fallback: if the project has no upcoming milestones, surface the
        // earliest open task on the project as the widget's headline item.
        val upcomingMilestoneTitle = aggregate?.upcomingMilestoneTitle
        val nextDueTaskTitle = if (upcomingMilestoneTitle == null) {
            db.taskDao().getTasksByProjectOnce(projectId)
                .asSequence()
                .filter { !it.isCompleted && it.parentTaskId == null && it.archivedAt == null }
                .sortedWith(compareBy({ it.dueDate ?: Long.MAX_VALUE }, { -it.priority }))
                .firstOrNull()
                ?.title
        } else {
            null
        }

        return ProjectWidgetData(
            projectId = project.id,
            name = project.name,
            icon = project.icon,
            themeColorHex = project.themeColorKey ?: project.color,
            status = project.status,
            milestoneProgress = if ((aggregate?.totalMilestones ?: 0) == 0) {
                0f
            } else {
                (aggregate!!.completedMilestones.toFloat() / aggregate.totalMilestones).coerceIn(0f, 1f)
            },
            completedMilestones = aggregate?.completedMilestones ?: 0,
            totalMilestones = aggregate?.totalMilestones ?: 0,
            upcomingMilestoneTitle = upcomingMilestoneTitle,
            nextDueTaskTitle = nextDueTaskTitle,
            totalTasks = aggregate?.totalTasks ?: 0,
            openTasks = aggregate?.openTasks ?: 0,
            streak = streak,
            daysSinceActivity = daysSince
        )
    }

    suspend fun getTopTemplates(context: Context, limit: Int = 3): List<TemplateShortcut> {
        val db = getDb(context)
        return db.taskTemplateDao().getAllTemplatesOnce().take(limit).map {
            TemplateShortcut(it.id, it.name, it.icon ?: "\uD83D\uDCCB")
        }
    }

    suspend fun toggleTaskCompletion(context: Context, taskId: Long) {
        val db = getDb(context)
        val taskDao = db.taskDao()
        val task = taskDao.getTaskByIdOnce(taskId) ?: return
        val now = System.currentTimeMillis()
        if (task.isCompleted) taskDao.markIncomplete(taskId, now) else taskDao.markCompleted(taskId, now)
    }

    suspend fun toggleHabitCompletion(context: Context, habitId: Long) {
        val db = getDb(context)
        val dayStartHour = context.readDayStartHour()
        val dayStartMinute = context.readDayStartMinute()
        val startOfDay = DayBoundary.startOfCurrentDay(
            dayStartHour = dayStartHour,
            dayStartMinute = dayStartMinute
        )
        val completionDao = db.habitCompletionDao()
        if (completionDao.isCompletedOnDateOnce(habitId, startOfDay)) {
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
    }
}
