package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.HabitLogEntity
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.usecase.StreakCalculator
import com.averycorp.prismtask.notifications.MedicationReminderScheduler
import com.averycorp.prismtask.util.DayBoundary
import com.averycorp.prismtask.widget.WidgetUpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    val dailyTarget: Int = 1,
    val isBookedThisPeriod: Boolean = false,
    val bookedTasksThisPeriod: Int = 0,
    val previousPeriodCompletions: Int = 0,
    val previousPeriodMet: Boolean = false,
    val lastLogDate: Long? = null,
    val logCount: Int = 0
)

@Singleton
class HabitRepository
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val completionDao: HabitCompletionDao,
        private val habitLogDao: HabitLogDao,
        private val taskDao: TaskDao,
        private val syncTracker: SyncTracker,
        private val medicationReminderScheduler: MedicationReminderScheduler,
        private val taskBehaviorPreferences: TaskBehaviorPreferences,
        private val habitListPreferences: HabitListPreferences,
        private val widgetUpdateManager: WidgetUpdateManager
    ) {
        private suspend fun currentDayStartHour(): Int = taskBehaviorPreferences.getDayStartHour().first()

        private suspend fun normalizeForToday(timestamp: Long): Long =
            DayBoundary.normalizeToDayStart(timestamp, currentDayStartHour())

        fun getAllHabits(): Flow<List<HabitEntity>> = habitDao.getAllHabits()

        fun getActiveHabits(): Flow<List<HabitEntity>> = habitDao.getActiveHabits()

        fun getHabitById(id: Long): Flow<HabitEntity?> = habitDao.getHabitById(id)

        suspend fun getHabitByIdOnce(id: Long): HabitEntity? = habitDao.getHabitByIdOnce(id)

        suspend fun addHabit(habit: HabitEntity): Long {
            val now = System.currentTimeMillis()
            val id = habitDao.insert(habit.copy(createdAt = now, updatedAt = now))
            syncTracker.trackCreate(id, "habit")
            widgetUpdateManager.updateHabitWidgets()
            return id
        }

        suspend fun updateHabit(habit: HabitEntity) {
            habitDao.update(habit.copy(updatedAt = System.currentTimeMillis()))
            syncTracker.trackUpdate(habit.id, "habit")
            widgetUpdateManager.updateHabitWidgets()
        }

        suspend fun deleteHabit(id: Long) {
            medicationReminderScheduler.cancel(id)
            syncTracker.trackDelete(id, "habit")
            habitDao.deleteById(id)
            widgetUpdateManager.updateHabitWidgets()
        }

        /**
         * Forgiveness-aware streak result for a single habit (v1.4.0 V5).
         *
         * Returns [com.averycorp.prismtask.domain.usecase.StreakResult] so the
         * caller gets both the strict count and the resilient count with the
         * configured grace window. Daily habits use the forgiving walk;
         * other frequencies fall back to strict (see
         * [com.averycorp.prismtask.domain.usecase.StreakCalculator.calculateResilientStreak]).
         */
        suspend fun getResilientStreak(
            habitId: Long,
            config: com.averycorp.prismtask.domain.usecase.ForgivenessConfig =
                com.averycorp.prismtask.domain.usecase.ForgivenessConfig.DEFAULT
        ): com.averycorp.prismtask.domain.usecase.StreakResult? {
            val habit = habitDao.getHabitByIdOnce(habitId) ?: return null
            val completions = completionDao.getCompletionsForHabitOnce(habitId)
            return com.averycorp.prismtask.domain.usecase.StreakCalculator
                .calculateResilientStreak(completions, habit, config = config)
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
            val normalizedDate = normalizeForToday(date)
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
            widgetUpdateManager.updateHabitWidgets()

            if (habit != null && habit.reminderIntervalMillis != null) {
                val newCount = currentCount + 1
                if (newCount < timesPerDay) {
                    medicationReminderScheduler.scheduleNext(
                        habitId,
                        habit.name,
                        habit.description,
                        now,
                        habit.reminderIntervalMillis,
                        doseNumber = newCount + 1,
                        totalDoses = timesPerDay
                    )
                }
            }
        }

        suspend fun uncompleteHabit(habitId: Long, date: Long) {
            val normalizedDate = normalizeForToday(date)
            val completion = completionDao.getByHabitAndDate(habitId, normalizedDate)
            if (completion != null) {
                syncTracker.trackDelete(completion.id, "habit_completion")
            }
            completionDao.deleteLatestByHabitAndDate(habitId, normalizedDate)
            widgetUpdateManager.updateHabitWidgets()

            // Reschedule from previous completion or cancel if none remain
            val habit = habitDao.getHabitByIdOnce(habitId)
            if (habit?.reminderIntervalMillis != null) {
                val previousCompletion = completionDao.getLastCompletionOnce(habitId)
                val timesPerDay = habit.reminderTimesPerDay
                val newCount = completionDao.getCompletionCountForDateOnce(habitId, normalizedDate)
                if (previousCompletion != null && newCount < timesPerDay) {
                    medicationReminderScheduler.scheduleNext(
                        habitId,
                        habit.name,
                        habit.description,
                        previousCompletion.completedAt,
                        habit.reminderIntervalMillis,
                        doseNumber = newCount + 1,
                        totalDoses = timesPerDay
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

        suspend fun getAllCategories(): List<String> = habitDao.getAllCategories()

        suspend fun updateSortOrders(habits: List<HabitEntity>) {
            habitDao.updateAll(habits)
        }

        // --- Bookable habit log methods ---

        fun getLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>> =
            habitLogDao.getLogsForHabit(habitId)

        suspend fun logActivity(habitId: Long, date: Long, notes: String?): Long {
            val log = HabitLogEntity(
                habitId = habitId,
                date = date,
                notes = notes?.trim()?.ifEmpty { null }
            )
            val logId = habitLogDao.insertLog(log)
            syncTracker.trackCreate(logId, "habit_log")

            val habit = habitDao.getHabitByIdOnce(habitId)
            if (habit != null) {
                if (habit.isBooked) {
                    // Fulfilling the booking — reset booking fields
                    habitDao.update(
                        habit.copy(
                            isBooked = false,
                            bookedDate = null,
                            bookedNote = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    // Touch updatedAt so the active-habits flow re-emits and lastLogDate refreshes
                    habitDao.update(habit.copy(updatedAt = System.currentTimeMillis()))
                }
                syncTracker.trackUpdate(habitId, "habit")
            }
            return logId
        }

        suspend fun setBooked(habitId: Long, isBooked: Boolean, bookedDate: Long?, bookedNote: String?) {
            val habit = habitDao.getHabitByIdOnce(habitId) ?: return
            habitDao.update(
                habit.copy(
                    isBooked = isBooked,
                    bookedDate = if (isBooked) bookedDate else null,
                    bookedNote = if (isBooked) bookedNote?.trim()?.ifEmpty { null } else null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            syncTracker.trackUpdate(habitId, "habit")
        }

        suspend fun getLastLogDate(habitId: Long): Long? =
            habitLogDao.getLastLog(habitId)?.date

        suspend fun deleteLog(log: HabitLogEntity) {
            syncTracker.trackDelete(log.id, "habit_log")
            habitLogDao.deleteLog(log)
        }

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun getHabitsWithTodayStatus(): Flow<List<HabitWithStatus>> =
            taskBehaviorPreferences.getDayStartHour().flatMapLatest { dayStartHour ->
                val today = DayBoundary.startOfCurrentDay(dayStartHour)
                combine(
                    habitDao.getActiveHabits(),
                    completionDao.getCompletionsForDate(today)
                ) { habits, todayCompletions ->
                    val countByHabit = todayCompletions.groupBy { it.habitId }.mapValues { it.value.size }
                    habits.map { habit ->
                        val target = if (habit.reminderIntervalMillis != null) {
                            habit.reminderTimesPerDay
                        } else if (habit.frequencyPeriod == "daily") {
                            habit.targetFrequency
                        } else {
                            1
                        }
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

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun getHabitsWithFullStatus(): Flow<List<HabitWithStatus>> =
            taskBehaviorPreferences.getDayStartHour().flatMapLatest { dayStartHour ->
                val today = DayBoundary.startOfCurrentDay(dayStartHour)
                val weekStart = getWeekStart(today)
                val weekEnd = getWeekEnd(today)
                val todayLocal = LocalDate.now()

                combine(
                    habitDao.getActiveHabits(),
                    completionDao.getCompletionsForDate(today),
                    habitLogDao.getAllLogs(),
                    habitListPreferences.getStreakMaxMissedDays()
                ) { habits, todayCompletions, allLogs, streakMaxMissedDays ->
                    val countByHabit = todayCompletions.groupBy { it.habitId }.mapValues { it.value.size }
                    val logsByHabit = allLogs.groupBy { it.habitId }
                    habits.map { habit ->
                        val completions = completionDao.getCompletionsForHabitOnce(habit.id)
                        val target = if (habit.reminderIntervalMillis != null) {
                            habit.reminderTimesPerDay
                        } else if (habit.frequencyPeriod == "daily") {
                            habit.targetFrequency
                        } else {
                            1
                        }
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
                            "bimonthly" -> {
                                periodStart = getBimonthStart(today)
                                periodEnd = getBimonthEnd(today)
                            }
                            "quarterly" -> {
                                periodStart = getQuarterStart(today)
                                periodEnd = getQuarterEnd(today)
                            }
                            else -> {
                                periodStart = weekStart
                                periodEnd = weekEnd
                            }
                        }

                        val periodCompletions = completions
                            .filter { it.completedDate in periodStart..periodEnd }
                            .groupBy { it.completedDate }
                            .count { (_, dayCompletions) -> dayCompletions.size >= target }

                        // For non-daily habits, "completed today" means the period target is met
                        val isCompleted = when (habit.frequencyPeriod) {
                            "daily" -> count >= target
                            else -> periodCompletions >= habit.targetFrequency
                        }

                        // Previous-period completion tracking
                        val (prevStart, prevEnd) = previousPeriodBounds(habit.frequencyPeriod, periodStart)
                        val previousCount = if (habit.trackPreviousPeriod && prevStart != null && prevEnd != null) {
                            completions
                                .filter { it.completedDate in prevStart..prevEnd }
                                .groupBy { it.completedDate }
                                .count { (_, dayCompletions) -> dayCompletions.size >= target }
                        } else {
                            0
                        }
                        val previousMet = habit.trackPreviousPeriod && previousCount >= habit.targetFrequency

                        // Booking tracking: tasks scoped to this habit scheduled within the current period
                        val bookedTasks = if (habit.trackBooking) {
                            taskDao.getTasksForHabitInRangeOnce(habit.id, periodStart, periodEnd).size
                        } else {
                            0
                        }
                        val isBooked = habit.trackBooking && bookedTasks > 0

                        // Bookable habit: fetch last log date and count from the observed logs flow
                        // so that adding/removing a log immediately refreshes the "Last done" display.
                        val habitLogs = if (habit.isBookable) logsByHabit[habit.id].orEmpty() else emptyList()
                        val lastLog = habitLogs.maxByOrNull { it.date }
                        val logTotal = habitLogs.size

                        HabitWithStatus(
                            habit = habit,
                            isCompletedToday = isCompleted,
                            currentStreak = StreakCalculator.calculateCurrentStreak(completions, habit, todayLocal, streakMaxMissedDays),
                            completionsThisWeek = periodCompletions,
                            completionsToday = count,
                            dailyTarget = target,
                            isBookedThisPeriod = isBooked,
                            bookedTasksThisPeriod = bookedTasks,
                            previousPeriodCompletions = previousCount,
                            previousPeriodMet = previousMet,
                            lastLogDate = lastLog?.date,
                            logCount = logTotal
                        )
                    }
                }
            }

        /**
         * Returns the [start, end] inclusive bounds of the period immediately preceding
         * the one that contains [currentPeriodStart]. Returns (null, null) for "daily"
         * or unknown periods — previous-period tracking only applies to recurring habits.
         */
        private fun previousPeriodBounds(frequencyPeriod: String, currentPeriodStart: Long): Pair<Long?, Long?> {
            val cal = Calendar.getInstance()
            return when (frequencyPeriod) {
                "weekly" -> {
                    cal.timeInMillis = currentPeriodStart
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                    val start = cal.timeInMillis
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                    cal.add(Calendar.MILLISECOND, -1)
                    start to cal.timeInMillis
                }
                "fortnightly" -> {
                    cal.timeInMillis = currentPeriodStart
                    cal.add(Calendar.WEEK_OF_YEAR, -2)
                    val start = cal.timeInMillis
                    cal.add(Calendar.WEEK_OF_YEAR, 2)
                    cal.add(Calendar.MILLISECOND, -1)
                    start to cal.timeInMillis
                }
                "monthly" -> {
                    cal.timeInMillis = currentPeriodStart
                    cal.add(Calendar.MONTH, -1)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.MILLISECOND, -1)
                    start to cal.timeInMillis
                }
                "bimonthly" -> {
                    cal.timeInMillis = currentPeriodStart
                    cal.add(Calendar.MONTH, -2)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 2)
                    cal.add(Calendar.MILLISECOND, -1)
                    start to cal.timeInMillis
                }
                "quarterly" -> {
                    cal.timeInMillis = currentPeriodStart
                    cal.add(Calendar.MONTH, -3)
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 3)
                    cal.add(Calendar.MILLISECOND, -1)
                    start to cal.timeInMillis
                }
                else -> null to null
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
                cal.firstDayOfWeek = Calendar.MONDAY
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
                cal.firstDayOfWeek = Calendar.MONDAY
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

            fun getBimonthStart(today: Long): Long {
                val cal = Calendar.getInstance()
                cal.timeInMillis = today
                // Align to 2-month periods starting from January (Jan-Feb, Mar-Apr, etc.)
                val month = cal.get(Calendar.MONTH) // 0-based
                val startMonth = if (month % 2 == 0) month else month - 1
                cal.set(Calendar.MONTH, startMonth)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            fun getBimonthEnd(today: Long): Long {
                val cal = Calendar.getInstance()
                cal.timeInMillis = getBimonthStart(today)
                cal.add(Calendar.MONTH, 2)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                return cal.timeInMillis
            }

            fun getQuarterStart(today: Long): Long {
                val cal = Calendar.getInstance()
                cal.timeInMillis = today
                val month = cal.get(Calendar.MONTH) // 0-based
                val startMonth = (month / 3) * 3
                cal.set(Calendar.MONTH, startMonth)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            fun getQuarterEnd(today: Long): Long {
                val cal = Calendar.getInstance()
                cal.timeInMillis = getQuarterStart(today)
                cal.add(Calendar.MONTH, 3)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
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
