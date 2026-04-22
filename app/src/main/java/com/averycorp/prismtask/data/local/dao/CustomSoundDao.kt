package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.CustomSoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSoundDao {
    @Query("SELECT * FROM custom_sounds ORDER BY created_at DESC")
    fun getAll(): Flow<List<CustomSoundEntity>>

    @Query("SELECT * FROM custom_sounds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CustomSoundEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: CustomSoundEntity): Long

    @Delete
    suspend fun delete(sound: CustomSoundEntity)

    @Query("DELETE FROM custom_sounds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM custom_sounds")
    suspend fun count(): Int

    @Query("SELECT * FROM custom_sounds ORDER BY created_at DESC")
    suspend fun getAllOnce(): List<CustomSoundEntity>

    @Query("DELETE FROM custom_sounds")
    suspend fun deleteAll()

    @Query("SELECT * FROM custom_sounds WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): CustomSoundEntity?

    @Query("UPDATE custom_sounds SET cloud_id = :cloudId, updated_at = :now WHERE id = :id")
    suspend fun setCloudId(id: Long, cloudId: String?, now: Long)

    @Update
    suspend fun update(sound: CustomSoundEntity)
}
