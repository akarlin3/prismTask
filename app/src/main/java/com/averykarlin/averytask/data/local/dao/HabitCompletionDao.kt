package com.averykarlin.averytask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCompletionDao {

    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId ORDER BY completed_date DESC")
    fun getCompletionsForHabit(habitId: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE completed_date = :date")
    fun getCompletionsForDate(date: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId AND completed_date >= :startDate AND completed_date <= :endDate ORDER BY completed_date ASC")
    fun getCompletionsInRange(habitId: Long, startDate: Long, endDate: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT COUNT(*) FROM habit_completions WHERE habit_id = :habitId AND completed_date >= :startDate AND completed_date <= :endDate")
    fun getCompletionCountInRange(habitId: Long, startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM habit_completions WHERE habit_id = :habitId AND completed_date = :date)")
    fun isCompletedOnDate(habitId: Long, date: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM habit_completions WHERE habit_id = :habitId AND completed_date = :date)")
    suspend fun isCompletedOnDateOnce(habitId: Long, date: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: HabitCompletionEntity): Long

    @Query("DELETE FROM habit_completions WHERE habit_id = :habitId AND completed_date = :date")
    suspend fun deleteByHabitAndDate(habitId: Long, date: Long)

    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId ORDER BY completed_date DESC LIMIT 1")
    fun getLastCompletion(habitId: Long): Flow<HabitCompletionEntity?>

    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId ORDER BY completed_date DESC")
    suspend fun getCompletionsForHabitOnce(habitId: Long): List<HabitCompletionEntity>
}
