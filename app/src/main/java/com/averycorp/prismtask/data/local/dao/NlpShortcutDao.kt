package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.NlpShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NlpShortcutDao {

    @Query("SELECT * FROM nlp_shortcuts ORDER BY sort_order ASC, id ASC")
    fun getAll(): Flow<List<NlpShortcutEntity>>

    @Query("SELECT * FROM nlp_shortcuts ORDER BY sort_order ASC, id ASC")
    suspend fun getAllOnce(): List<NlpShortcutEntity>

    @Query("SELECT * FROM nlp_shortcuts WHERE trigger = :trigger LIMIT 1")
    suspend fun getByTrigger(trigger: String): NlpShortcutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shortcut: NlpShortcutEntity): Long

    @Update
    suspend fun update(shortcut: NlpShortcutEntity)

    @Delete
    suspend fun delete(shortcut: NlpShortcutEntity)

    @Query("DELETE FROM nlp_shortcuts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM nlp_shortcuts")
    suspend fun count(): Int
}
