package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.ExternalAnchorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalAnchorDao {
    @Query(
        "SELECT * FROM external_anchors WHERE project_id = :projectId " +
            "ORDER BY created_at DESC, id DESC"
    )
    fun observeAnchors(projectId: Long): Flow<List<ExternalAnchorEntity>>

    @Query(
        "SELECT * FROM external_anchors WHERE project_id = :projectId " +
            "ORDER BY created_at DESC, id DESC"
    )
    suspend fun getAnchorsOnce(projectId: Long): List<ExternalAnchorEntity>

    @Query(
        "SELECT * FROM external_anchors WHERE phase_id = :phaseId " +
            "ORDER BY created_at DESC, id DESC"
    )
    suspend fun getAnchorsForPhaseOnce(phaseId: Long): List<ExternalAnchorEntity>

    @Query("SELECT * FROM external_anchors ORDER BY project_id ASC, id ASC")
    suspend fun getAllOnce(): List<ExternalAnchorEntity>

    @Query("SELECT * FROM external_anchors WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ExternalAnchorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anchor: ExternalAnchorEntity): Long

    @Update
    suspend fun update(anchor: ExternalAnchorEntity)

    @Delete
    suspend fun delete(anchor: ExternalAnchorEntity)

    @Query("DELETE FROM external_anchors WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM external_anchors WHERE project_id = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
