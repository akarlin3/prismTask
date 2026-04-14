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

    @Query("DELETE FROM calendar_sync")
    suspend fun deleteAll()
}
