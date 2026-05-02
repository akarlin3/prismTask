package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY priority DESC, name ASC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    fun observeEnabled(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getEnabledOnce(): List<AutomationRuleEntity>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getByIdOnce(id: Long): AutomationRuleEntity?

    @Query("SELECT * FROM automation_rules WHERE template_key = :templateKey LIMIT 1")
    suspend fun getByTemplateKeyOnce(templateKey: String): AutomationRuleEntity?

    @Query("SELECT id FROM automation_rules WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun findIdByCloudId(cloudId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRuleEntity): Long

    @Update
    suspend fun update(rule: AutomationRuleEntity)

    @Query("UPDATE automation_rules SET enabled = :enabled, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Atomic counter bump used by the engine after a successful firing.
     * The `daily_fire_count` field is reset by [resetDailyCounter] when
     * the day rolls over (UTC-day comparison done in Kotlin so we don't
     * have to teach SQLite about [DayBoundary]).
     */
    @Query(
        """
        UPDATE automation_rules
        SET fire_count = fire_count + 1,
            daily_fire_count = daily_fire_count + 1,
            last_fired_at = :now,
            updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun incrementFireCount(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE automation_rules
        SET daily_fire_count = 0,
            daily_fire_count_date = :today,
            updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun resetDailyCounter(id: Long, today: String, now: Long = System.currentTimeMillis())

    /** Time-based triggers: rule ids whose trigger_json declares a "TIME_OF_DAY" trigger. */
    @Query(
        """
        SELECT * FROM automation_rules
        WHERE enabled = 1
          AND trigger_json LIKE '%"type":"TIME_OF_DAY"%'
        """
    )
    suspend fun getTimeBasedEnabledOnce(): List<AutomationRuleEntity>
}
