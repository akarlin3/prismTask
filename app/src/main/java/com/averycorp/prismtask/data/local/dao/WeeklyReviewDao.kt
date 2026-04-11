package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: WeeklyReviewEntity): Long

    @Query("SELECT * FROM weekly_reviews WHERE week_start_date = :weekStart LIMIT 1")
    suspend fun getByWeek(weekStart: Long): WeeklyReviewEntity?

    @Query("SELECT * FROM weekly_reviews ORDER BY week_start_date DESC")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>

    @Query("SELECT * FROM weekly_reviews ORDER BY week_start_date DESC LIMIT 1")
    suspend fun getMostRecent(): WeeklyReviewEntity?

    @Query("DELETE FROM weekly_reviews WHERE id = :id")
    suspend fun delete(id: Long)
}
