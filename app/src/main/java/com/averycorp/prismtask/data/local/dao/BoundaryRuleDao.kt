package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.BoundaryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoundaryRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BoundaryRuleEntity): Long

    @Update
    suspend fun update(rule: BoundaryRuleEntity)

    @Query("SELECT * FROM boundary_rules ORDER BY is_enabled DESC, id ASC")
    fun observeAll(): Flow<List<BoundaryRuleEntity>>

    @Query("SELECT * FROM boundary_rules")
    suspend fun getAll(): List<BoundaryRuleEntity>

    @Query("SELECT COUNT(*) FROM boundary_rules")
    suspend fun count(): Int

    @Query("DELETE FROM boundary_rules WHERE id = :id")
    suspend fun delete(id: Long)
}
