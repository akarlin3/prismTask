package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.LeisureDao
import com.averykarlin.averytask.data.local.entity.LeisureLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeisureRepository @Inject constructor(
    private val leisureDao: LeisureDao
) {

    fun getTodayLog(): Flow<LeisureLogEntity?> = leisureDao.getLogForDate(todayMidnight())

    suspend fun setMusicPick(activityId: String) {
        val today = todayMidnight()
        val existing = leisureDao.getLogForDateOnce(today)
        if (existing != null) {
            leisureDao.updateLog(
                existing.copy(
                    musicPick = activityId,
                    musicDone = false,
                    startedAt = existing.startedAt ?: System.currentTimeMillis()
                )
            )
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
        val today = todayMidnight()
        val existing = leisureDao.getLogForDateOnce(today)
        if (existing != null) {
            leisureDao.updateLog(
                existing.copy(
                    flexPick = activityId,
                    flexDone = false,
                    startedAt = existing.startedAt ?: System.currentTimeMillis()
                )
            )
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
        val existing = leisureDao.getLogForDateOnce(todayMidnight()) ?: return
        leisureDao.updateLog(existing.copy(musicDone = done))
    }

    suspend fun toggleFlexDone(done: Boolean) {
        val existing = leisureDao.getLogForDateOnce(todayMidnight()) ?: return
        leisureDao.updateLog(existing.copy(flexDone = done))
    }

    suspend fun clearMusicPick() {
        val existing = leisureDao.getLogForDateOnce(todayMidnight()) ?: return
        leisureDao.updateLog(existing.copy(musicPick = null, musicDone = false))
    }

    suspend fun clearFlexPick() {
        val existing = leisureDao.getLogForDateOnce(todayMidnight()) ?: return
        leisureDao.updateLog(existing.copy(flexPick = null, flexDone = false))
    }

    suspend fun resetToday() {
        val existing = leisureDao.getLogForDateOnce(todayMidnight()) ?: return
        leisureDao.updateLog(
            existing.copy(
                musicPick = null,
                musicDone = false,
                flexPick = null,
                flexDone = false,
                startedAt = null
            )
        )
    }

    companion object {
        fun todayMidnight(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
