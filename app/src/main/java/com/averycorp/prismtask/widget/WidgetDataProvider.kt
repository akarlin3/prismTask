package com.averycorp.prismtask.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.averycorp.prismtask.data.local.database.AveryTaskDatabase
import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetTaskBehaviorPrefs by preferencesDataStore(name = "task_behavior_prefs")
private val DAY_START_HOUR_KEY = intPreferencesKey("day_start_hour")

private suspend fun Context.readDayStartHour(): Int =
    widgetTaskBehaviorPrefs.data.map { it[DAY_START_HOUR_KEY] ?: 0 }.first()

data class TodayWidgetData(
    val totalTasks: Int,
    val completedTasks: Int,
    val taskNames: List<String>,
    val totalHabits: Int,
    val completedHabits: Int
)

data class HabitWidgetData(
    val habits: List<HabitWidgetItem>
)

data class HabitWidgetItem(
    val id: Long,
    val name: String,
    val icon: String,
    val streak: Int,
    val isCompletedToday: Boolean
)

object WidgetDataProvider {

    private fun getDb(context: Context): AveryTaskDatabase =
        Room.databaseBuilder(context, AveryTaskDatabase::class.java, "averytask.db")
            .addMigrations(
                AveryTaskDatabase.MIGRATION_1_2, AveryTaskDatabase.MIGRATION_2_3,
                AveryTaskDatabase.MIGRATION_3_4, AveryTaskDatabase.MIGRATION_4_5,
                AveryTaskDatabase.MIGRATION_5_6, AveryTaskDatabase.MIGRATION_6_7
            )
            .build()

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

            return TodayWidgetData(
                totalTasks = allTasks.size,
                completedTasks = completedToday.size,
                taskNames = allTasks.take(6).map { it.title },
                totalHabits = habits.size,
                completedHabits = completedHabits
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

            val items = habits.take(6).map { habit ->
                val isCompleted = completionDao.isCompletedOnDateOnce(habit.id, startOfDay)
                HabitWidgetItem(
                    id = habit.id,
                    name = habit.name,
                    icon = habit.icon,
                    streak = 0, // streak omitted for widget perf
                    isCompletedToday = isCompleted
                )
            }

            return HabitWidgetData(habits = items)
        } finally {
            db.close()
        }
    }
}
