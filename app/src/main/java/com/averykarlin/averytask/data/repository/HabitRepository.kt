package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.data.remote.SyncTracker
import com.averykarlin.averytask.domain.usecase.StreakCalculator
import com.averykarlin.averytask.notifications.MedicationReminderScheduler
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
    val completionsThisWeek: Int,
    val completionsToday: Int = if (isCompletedToday) 1 else 0,
    val dailyTarget: Int = 1
)

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao,
    private val syncTracker: SyncTracker,
    private val medicationReminderScheduler: MedicationReminderScheduler
) {
    fun getAllHabits(): Flow<List<HabitEntity>> = habitDao.getAllHabits()

    fun getActiveHabits(): Flow<List<HabitEntity>> = habitDao.getActiveHabits()

    fun getHabitById(id: Long): Flow<HabitEntity?> = habitDao.getHabitById(id)

    suspend fun getHabitByIdOnce(id: Long): HabitEntity? = habitDao.getHabitByIdOnce(id)

    suspend fun addHabit(habit: HabitEntity): Long {
        val now = System.currentTimeMillis()
        val id = habitDao.insert(habit.copy(createdAt = now, updatedAt = now))
        syncTracker.trackCreate(id, "habit")
        return id
    }

    suspend fun updateHabit(habit: HabitEntity) {
        habitDao.update(habit.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(habit.id, "habit")
    }

    suspend fun deleteHabit(id: Long) {
        medicationReminderScheduler.cancel(id)
        syncTracker.trackDelete(id, "habit")
        habitDao.deleteById(id)
    }

    suspend fun archiveHabit(id: Long) {
        val habit = habitDao.getHabitByIdOnce(id) ?: return
        medicationReminderScheduler.cancel(id)
        habitDao.update(habit.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(id, "habit")
    }

    suspend fun unarchiveHabit(id: Long) {
        val habit = habitDao.getHabitByIdOnce(id) ?: return
        habitDao.update(habit.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(id, "habit")
    }

    suspend fun completeHabit(habitId: Long, date: Long, notes: String? = null) {
        val normalizedDate = normalizeToMidnight(date)
        val habit = habitDao.getHabitByIdOnce(habitId)
        val hasMedInterval = habit?.reminderIntervalMillis != null
        val timesPerDay = habit?.reminderTimesPerDay ?: 1
        val target = if (habit?.frequencyPeriod == "daily") habit.targetFrequency else 1
        val currentCount = completionDao.getCompletionCountForDateOnce(habitId, normalizedDate)

        // Allow completion if under target, or up to timesPerDay for medication-interval habits
        if (hasMedInterval) {
            if (currentCount >= timesPerDay) return
        } else {
            if (currentCount >= target) return
        }

        val now = System.currentTimeMillis()
        val id = completionDao.insert(
            HabitCompletionEntity(
                habitId = habitId,
                completedDate = normalizedDate,
                completedAt = now,
                notes = notes?.trim()?.ifEmpty { null }
            )
        )
        syncTracker.trackCreate(id, "habit_completion")

        if (habit != null && habit.reminderIntervalMillis != null) {
            val newCount = currentCount + 1
            if (newCount < timesPerDay) {
                medicationReminderScheduler.scheduleNext(
                    habitId, habit.name, habit.description, now, habit.reminderIntervalMillis,
                    doseNumber = newCount + 1, totalDoses = timesPerDay
                )
            }
        }
    }

    suspend fun uncompleteHabit(habitId: Long, date: Long) {
        val normalizedDate = normalizeToMidnight(date)
        val completion = completionDao.getByHabitAndDate(habitId, normalizedDate)
        if (completion != null) {
            syncTracker.trackDelete(completion.id, "habit_completion")
        }
        completionDao.deleteLatestByHabitAndDate(habitId, normalizedDate)

        // Reschedule from previous completion or cancel if none remain
        val habit = habitDao.getHabitByIdOnce(habitId)
        if (habit?.reminderIntervalMillis != null) {
            val previousCompletion = completionDao.getLastCompletionOnce(habitId)
            val timesPerDay = habit.reminderTimesPerDay
            val newCount = completionDao.getCompletionCountForDateOnce(habitId, normalizedDate)
            if (previousCompletion != null && newCount < timesPerDay) {
                medicationReminderScheduler.scheduleNext(
                    habitId, habit.name, habit.description,
                    previousCompletion.completedAt, habit.reminderIntervalMillis,
                    doseNumber = newCount + 1, totalDoses = timesPerDay
                )
            } else {
                medicationReminderScheduler.cancel(habitId)
            }
        }
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

        return combine(
            habitDao.getActiveHabits(),
            completionDao.getCompletionsForDate(today)
        ) { habits, todayCompletions ->
            val countByHabit = todayCompletions.groupBy { it.habitId }.mapValues { it.value.size }
            habits.map { habit ->
                val target = if (habit.reminderIntervalMillis != null) {
                    habit.reminderTimesPerDay
                } else if (habit.frequencyPeriod == "daily") {
                    habit.targetFrequency
                } else 1
                val count = countByHabit[habit.id] ?: 0
                HabitWithStatus(
                    habit = habit,
                    isCompletedToday = count >= target,
                    currentStreak = 0,
                    completionsThisWeek = 0,
                    completionsToday = count,
                    dailyTarget = target
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
            val countByHabit = todayCompletions.groupBy { it.habitId }.mapValues { it.value.size }
            habits.map { habit ->
                val completions = completionDao.getCompletionsForHabitOnce(habit.id)
                val target = if (habit.reminderIntervalMillis != null) {
                    habit.reminderTimesPerDay
                } else if (habit.frequencyPeriod == "daily") {
                    habit.targetFrequency
                } else 1
                val count = countByHabit[habit.id] ?: 0

                val periodStart: Long
                val periodEnd: Long
                when (habit.frequencyPeriod) {
                    "fortnightly" -> {
                        periodStart = getFortnightStart(today)
                        periodEnd = getFortnightEnd(today)
                    }
                    "monthly" -> {
                        periodStart = getMonthStart(today)
                        periodEnd = getMonthEnd(today)
                    }
                    else -> {
                        periodStart = weekStart
                        periodEnd = weekEnd
                    }
                }

                val periodCompletions = completions.filter { it.completedDate in periodStart..periodEnd }
                    .groupBy { it.completedDate }
                    .count { (_, dayCompletions) -> dayCompletions.size >= target }

                // For non-daily habits, "completed today" means the period target is met
                val isCompleted = when (habit.frequencyPeriod) {
                    "daily" -> count >= target
                    else -> periodCompletions >= habit.targetFrequency
                }

                HabitWithStatus(
                    habit = habit,
                    isCompletedToday = isCompleted,
                    currentStreak = StreakCalculator.calculateCurrentStreak(completions, habit, todayLocal),
                    completionsThisWeek = periodCompletions,
                    completionsToday = count,
                    dailyTarget = target
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

        fun getFortnightStart(today: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = today
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > today) cal.add(Calendar.WEEK_OF_YEAR, -1)
            // Use ISO week number to determine fortnight boundary (odd weeks start a fortnight)
            val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
            if (weekNum % 2 == 0) cal.add(Calendar.WEEK_OF_YEAR, -1)
            return cal.timeInMillis
        }

        fun getFortnightEnd(today: Long): Long {
            val start = getFortnightStart(today)
            val cal = Calendar.getInstance()
            cal.timeInMillis = start
            cal.add(Calendar.DAY_OF_YEAR, 13)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return cal.timeInMillis
        }

        fun getMonthStart(today: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = today
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun getMonthEnd(today: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = today
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return cal.timeInMillis
        }
    }
}
