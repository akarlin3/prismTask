package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE taskId = :taskId ORDER BY created_at ASC")
    fun getAttachmentsForTask(taskId: Long): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE taskId = :taskId")
    suspend fun deleteAllForTask(taskId: Long)

    @Query("SELECT COUNT(*) FROM attachments WHERE taskId = :taskId")
    fun getAttachmentCountForTask(taskId: Long): Flow<Int>

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}
