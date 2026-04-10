package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.LeisureLogEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LeisureRepository @Inject constructor(
    private val leisureDao: LeisureDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val taskBehaviorPreferences: TaskBehaviorPreferences
) {

    private suspend fun startOfToday(): Long =
        DayBoundary.startOfCurrentDay(taskBehaviorPreferences.getDayStartHour().first())

    fun getTodayLog(): Flow<LeisureLogEntity?> =
        taskBehaviorPreferences.getDayStartHour().flatMapLatest { hour ->
            leisureDao.getLogForDate(DayBoundary.startOfCurrentDay(hour))
        }

    suspend fun setMusicPick(activityId: String) {
        val today = startOfToday()
        val existing = leisureDao.getLogForDateOnce(today)
        if (existing != null) {
            leisureDao.updateLog(
                existing.copy(
                    musicPick = activityId,
                    musicDone = false,
                    startedAt = existing.startedAt ?: System.currentTimeMillis()
                )
            )
            syncHabitCompletion(existing.copy(musicPick = activityId, musicDone = false))
        } else {
            leisureDao.insertLog(
                LeisureLogEntity(
                    date = today,
                    musicPick = activityId,
                    startedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun setFlexPick(activityId: String) {
        val today = startOfToday()
        val existing = leisureDao.getLogForDateOnce(today)
        if (existing != null) {
            leisureDao.updateLog(
                existing.copy(
                    flexPick = activityId,
                    flexDone = false,
                    startedAt = existing.startedAt ?: System.currentTimeMillis()
                )
            )
            syncHabitCompletion(existing.copy(flexPick = activityId, flexDone = false))
        } else {
            leisureDao.insertLog(
                LeisureLogEntity(
                    date = today,
                    flexPick = activityId,
                    startedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun toggleMusicDone(done: Boolean) {
        val existing = leisureDao.getLogForDateOnce(startOfToday()) ?: return
        val updated = existing.copy(musicDone = done)
        leisureDao.updateLog(updated)
        syncHabitCompletion(updated)
    }

    suspend fun toggleFlexDone(done: Boolean) {
        val existing = leisureDao.getLogForDateOnce(startOfToday()) ?: return
        val updated = existing.copy(flexDone = done)
        leisureDao.updateLog(updated)
        syncHabitCompletion(updated)
    }

    suspend fun clearMusicPick() {
        val existing = leisureDao.getLogForDateOnce(startOfToday()) ?: return
        val updated = existing.copy(musicPick = null, musicDone = false)
        leisureDao.updateLog(updated)
        syncHabitCompletion(updated)
    }

    suspend fun clearFlexPick() {
        val existing = leisureDao.getLogForDateOnce(startOfToday()) ?: return
        val updated = existing.copy(flexPick = null, flexDone = false)
        leisureDao.updateLog(updated)
        syncHabitCompletion(updated)
    }

    suspend fun resetToday() {
        val existing = leisureDao.getLogForDateOnce(startOfToday()) ?: return
        val updated = existing.copy(
            musicPick = null,
            musicDone = false,
            flexPick = null,
            flexDone = false,
            startedAt = null
        )
        leisureDao.updateLog(updated)
        syncHabitCompletion(updated)
    }

    suspend fun ensureHabitExists() {
        getOrCreateLeisureHabit()
    }

    private suspend fun getOrCreateLeisureHabit(): HabitEntity {
        val existing = habitDao.getHabitByName(LEISURE_HABIT_NAME)
        if (existing != null) return existing
        val id = habitDao.insert(
            HabitEntity(
                name = LEISURE_HABIT_NAME,
                description = "Complete both daily leisure activities (music + flex)",
                icon = "\uD83C\uDFB5",
                color = "#8B5CF6",
                category = "Leisure",
                targetFrequency = 1,
                frequencyPeriod = "daily"
            )
        )
        return habitDao.getHabitByIdOnce(id)!!
    }

    private suspend fun syncHabitCompletion(log: LeisureLogEntity) {
        val habit = getOrCreateLeisureHabit()
        val today = startOfToday()
        val allDone = log.musicDone && log.flexDone
        val alreadyCompleted = habitCompletionDao.isCompletedOnDateOnce(habit.id, today)

        if (allDone && !alreadyCompleted) {
            habitCompletionDao.insert(
                HabitCompletionEntity(
                    habitId = habit.id,
                    completedDate = today,
                    completedAt = System.currentTimeMillis()
                )
            )
        } else if (!allDone && alreadyCompleted) {
            habitCompletionDao.deleteByHabitAndDate(habit.id, today)
        }
    }

    companion object {
        const val LEISURE_HABIT_NAME = "Leisure"
    }
}
