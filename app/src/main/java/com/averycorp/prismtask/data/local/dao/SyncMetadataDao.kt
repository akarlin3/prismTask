package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE local_id = :localId AND entity_type = :entityType")
    suspend fun get(localId: Long, entityType: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE pending_action IS NOT NULL ORDER BY entity_type ASC")
    suspend fun getPendingActions(): List<SyncMetadataEntity>

    @Query("SELECT * FROM sync_metadata WHERE pending_action IS NOT NULL")
    fun observePending(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT COUNT(*) FROM sync_metadata WHERE pending_action IS NOT NULL")
    fun getPendingCount(): Flow<Int>

    @Query("UPDATE sync_metadata SET pending_action = NULL, last_synced_at = :now, retry_count = 0 WHERE local_id = :localId AND entity_type = :entityType")
    suspend fun clearPendingAction(localId: Long, entityType: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_metadata WHERE local_id = :localId AND entity_type = :entityType")
    suspend fun delete(localId: Long, entityType: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAll()

    @Query("SELECT cloud_id FROM sync_metadata WHERE local_id = :localId AND entity_type = :entityType")
    suspend fun getCloudId(localId: Long, entityType: String): String?

    @Query("SELECT local_id FROM sync_metadata WHERE cloud_id = :cloudId AND entity_type = :entityType")
    suspend fun getLocalId(cloudId: String, entityType: String): Long?

    @Query("UPDATE sync_metadata SET retry_count = retry_count + 1 WHERE local_id = :localId AND entity_type = :entityType")
    suspend fun incrementRetry(localId: Long, entityType: String)
}
