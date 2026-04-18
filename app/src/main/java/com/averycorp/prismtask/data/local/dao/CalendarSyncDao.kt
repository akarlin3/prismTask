package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.CalendarSyncEntity

@Dao
interface CalendarSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarSyncEntity)

    @Query("SELECT * FROM calendar_sync WHERE task_id = :taskId")
    suspend fun getByTaskId(taskId: Long): CalendarSyncEntity?

    @Query("DELETE FROM calendar_sync WHERE task_id = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("SELECT calendar_event_id FROM calendar_sync WHERE task_id = :taskId")
    suspend fun getCalendarEventId(taskId: Long): String?

    @Query("SELECT * FROM calendar_sync WHERE calendar_id = :calendarId")
    suspend fun getByCalendarId(calendarId: String): List<CalendarSyncEntity>

    @Query("SELECT * FROM calendar_sync WHERE calendar_event_id = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: String): CalendarSyncEntity?

    @Query("SELECT * FROM calendar_sync WHERE sync_state = :state")
    suspend fun findByState(state: String): List<CalendarSyncEntity>

    @Query("UPDATE calendar_sync SET sync_state = :state WHERE task_id = :taskId")
    suspend fun updateState(taskId: Long, state: String)

    @Query("DELETE FROM calendar_sync")
    suspend fun deleteAll()

    @Query("SELECT * FROM calendar_sync")
    suspend fun getAllOnce(): List<CalendarSyncEntity>
}
