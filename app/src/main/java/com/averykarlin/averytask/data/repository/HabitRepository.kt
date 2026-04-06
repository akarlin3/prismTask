package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.domain.usecase.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class HabitWithStatus(
    val habit: HabitEntity,
    val isCompletedToday: Boolean,
    val currentStreak: Int,
    val completionsThisWeek: Int
)

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao
) {
    fun getAllHabits(): Flow<List<HabitEntity>> = habitDao.getAllHabits()

    fun getActiveHabits(): Flow<List<HabitEntity>> = habitDao.getActiveHabits()

    fun getHabitById(id: Long): Flow<HabitEntity?> = habitDao.getHabitById(id)

    suspend fun getHabitByIdOnce(id: Long): HabitEntity? = habitDao.getHabitByIdOnce(id)

    suspend fun addHabit(habit: HabitEntity): Long {
        val now = System.currentTimeMillis()
        return habitDao.insert(habit.copy(createdAt = now, updatedAt = now))
    }

    suspend fun updateHabit(habit: HabitEntity) {
        habitDao.update(habit.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteHabit(id: Long) {
        habitDao.deleteById(id)
    }

    suspend fun archiveHabit(id: Long) {
        val habit = habitDao.getHabitByIdOnce(id) ?: return
        habitDao.update(habit.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun unarchiveHabit(id: Long) {
        val habit = habitDao.getHabitByIdOnce(id) ?: return
        habitDao.update(habit.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun completeHabit(habitId: Long, date: Long) {
        val normalizedDate = normalizeToMidnight(date)
        if (completionDao.isCompletedOnDateOnce(habitId, normalizedDate)) return
        completionDao.insert(
            HabitCompletionEntity(
                habitId = habitId,
                completedDate = normalizedDate,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun uncompleteHabit(habitId: Long, date: Long) {
        completionDao.deleteByHabitAndDate(habitId, normalizeToMidnight(date))
    }

    fun getCompletionsForHabit(habitId: Long): Flow<List<HabitCompletionEntity>> =
        completionDao.getCompletionsForHabit(habitId)

    suspend fun getCompletionsForHabitOnce(habitId: Long): List<HabitCompletionEntity> =
        completionDao.getCompletionsForHabitOnce(habitId)

    fun getCompletionsInRange(habitId: Long, startDate: Long, endDate: Long): Flow<List<HabitCompletionEntity>> =
        completionDao.getCompletionsInRange(habitId, startDate, endDate)

    suspend fun updateSortOrders(habits: List<HabitEntity>) {
        habitDao.updateAll(habits)
    }

    fun getHabitsWithTodayStatus(): Flow<List<HabitWithStatus>> {
        val today = normalizeToMidnight(System.currentTimeMillis())
        val weekStart = getWeekStart(today)
        val weekEnd = getWeekEnd(today)

        return combine(
            habitDao.getActiveHabits(),
            completionDao.getCompletionsForDate(today)
        ) { habits, todayCompletions ->
            val todayCompletedIds = todayCompletions.map { it.habitId }.toSet()
            habits.map { habit ->
                HabitWithStatus(
                    habit = habit,
                    isCompletedToday = habit.id in todayCompletedIds,
                    currentStreak = 0, // computed lazily in detail/analytics
                    completionsThisWeek = 0 // placeholder, updated below
                )
            }
        }
    }

    fun getHabitsWithFullStatus(): Flow<List<HabitWithStatus>> {
        val today = normalizeToMidnight(System.currentTimeMillis())
        val weekStart = getWeekStart(today)
        val weekEnd = getWeekEnd(today)
        val todayLocal = LocalDate.now()

        return combine(
            habitDao.getActiveHabits(),
            completionDao.getCompletionsForDate(today)
        ) { habits, todayCompletions ->
            val todayCompletedIds = todayCompletions.map { it.habitId }.toSet()
            habits.map { habit ->
                val completions = completionDao.getCompletionsForHabitOnce(habit.id)
                val weekCompletions = completions.count { it.completedDate in weekStart..weekEnd }
                HabitWithStatus(
                    habit = habit,
                    isCompletedToday = habit.id in todayCompletedIds,
                    currentStreak = StreakCalculator.calculateCurrentStreak(completions, habit, todayLocal),
                    completionsThisWeek = weekCompletions
                )
            }
        }
    }

    companion object {
        fun normalizeToMidnight(timestamp: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun getWeekStart(today: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = today
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun getWeekEnd(today: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = today
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            if (cal.timeInMillis < today) cal.add(Calendar.WEEK_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }
}
