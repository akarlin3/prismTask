package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sort_order ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE is_archived = 0 ORDER BY sort_order ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitById(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitByIdOnce(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE name = :name LIMIT 1")
    suspend fun getHabitByName(name: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE is_archived = 0 ORDER BY sort_order ASC")
    suspend fun getActiveHabitsOnce(): List<HabitEntity>

    /**
     * Returns habits that should appear on a given day-of-week. [day] is a
     * `java.time.DayOfWeek.value` (1 = Monday ... 7 = Sunday), matching the
     * format written by the habit editor into `active_days` as e.g.
     * `[1,2,3,4,5]`. Habits with a null/empty `active_days` (daily habits
     * with no weekly restriction) are always considered active.
     */
    @Query(
        "SELECT * FROM habits " +
            "WHERE is_archived = 0 " +
            "AND (active_days IS NULL OR active_days = '' OR active_days LIKE '%' || :day || '%') " +
            "ORDER BY sort_order ASC"
    )
    fun getHabitsActiveForDay(day: Int): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sort_order ASC")
    suspend fun getAllHabitsOnce(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun updateAll(habits: List<HabitEntity>)

    @Query("SELECT * FROM habits WHERE reminder_interval_millis IS NOT NULL AND is_archived = 0")
    suspend fun getHabitsWithIntervalReminder(): List<HabitEntity>

    @Query(
        "SELECT * FROM habits WHERE reminder_time IS NOT NULL " +
            "AND reminder_interval_millis IS NULL AND is_archived = 0"
    )
    suspend fun getHabitsWithDailyTimeReminder(): List<HabitEntity>

    @Query("SELECT DISTINCT category FROM habits WHERE category IS NOT NULL AND category != '' ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT * FROM habits WHERE is_built_in = 1 ORDER BY name ASC")
    suspend fun getBuiltInHabitsOnce(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE template_key = :key LIMIT 1")
    suspend fun getByTemplateKeyOnce(key: String): HabitEntity?

    @Query(
        "UPDATE habits SET is_built_in = 1, template_key = :templateKey " +
            "WHERE name = :name AND (template_key IS NULL OR template_key = '')"
    )
    suspend fun backfillBuiltIn(name: String, templateKey: String): Int

    @Transaction
    suspend fun backfillAllBuiltIns(): Int {
        var count = 0
        count += backfillBuiltIn("School", "builtin_school")
        count += backfillBuiltIn("Leisure", "builtin_leisure")
        count += backfillBuiltIn("Morning Self-Care", "builtin_morning_selfcare")
        count += backfillBuiltIn("Bedtime Self-Care", "builtin_bedtime_selfcare")
        count += backfillBuiltIn("Medication", "builtin_medication")
        count += backfillBuiltIn("Housework", "builtin_housework")
        return count
    }

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}
