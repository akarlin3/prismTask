package com.averykarlin.averytask.widget

import android.content.Context
import androidx.room.Room
import com.averykarlin.averytask.data.local.database.AveryTaskDatabase
import com.averykarlin.averytask.data.repository.HabitRepository
import java.util.Calendar

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
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000

            val taskDao = db.taskDao()
            val todayTasks = taskDao.getTodayTasksOnce(startOfDay, endOfDay)
            val overdueTasks = taskDao.getOverdueRootTasksOnce(startOfDay)
            val allTasks = overdueTasks + todayTasks
            val completedToday = taskDao.getCompletedTodayOnce(startOfDay)

            val habitDao = db.habitDao()
            val habits = habitDao.getActiveHabitsOnce()
            val completionDao = db.habitCompletionDao()
            val todayMidnight = HabitRepository.normalizeToMidnight(System.currentTimeMillis())
            val completedHabits = habits.count { completionDao.isCompletedOnDateOnce(it.id, todayMidnight) }

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
            val todayMidnight = HabitRepository.normalizeToMidnight(System.currentTimeMillis())

            val items = habits.take(6).map { habit ->
                val isCompleted = completionDao.isCompletedOnDateOnce(habit.id, todayMidnight)
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
