package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.SavedFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFilterDao {

    @Query("SELECT * FROM saved_filters ORDER BY sort_order ASC, id ASC")
    fun getAll(): Flow<List<SavedFilterEntity>>

    @Query("SELECT * FROM saved_filters ORDER BY sort_order ASC, id ASC")
    suspend fun getAllOnce(): List<SavedFilterEntity>

    @Query("SELECT * FROM saved_filters WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SavedFilterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: SavedFilterEntity): Long

    @Update
    suspend fun update(filter: SavedFilterEntity)

    @Delete
    suspend fun delete(filter: SavedFilterEntity)

    @Query("DELETE FROM saved_filters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM saved_filters")
    suspend fun count(): Int
}
