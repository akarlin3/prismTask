package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent shape for a single automation rule.
 *
 * Storage strategy is the CAUSE-C hybrid from
 * `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` § A2: queryable metadata
 * columns (enabled, priority, last_fired_at, fire_count) plus three JSON
 * blobs holding the trigger / condition / action structure. Reads that need
 * to filter (e.g. "all enabled rules") avoid parsing JSON; reads that need
 * to execute parse via [AutomationJsonAdapter].
 *
 * Rate-limit columns ([dailyFireCount], [dailyFireCountDate]) are reset on
 * day boundary by [AutomationEngine.resetDailyCountIfNeeded] before each
 * execution. Per-rule cap defaults to [AutomationEngine.MAX_FIRES_PER_RULE_PER_DAY]
 * but can be overridden in JSON via `"maxFiresPerDay": N`.
 */
@Entity(
    tableName = "automation_rules",
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["enabled"])
    ]
)
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "template_key")
    val templateKey: String? = null,
    @ColumnInfo(name = "trigger_json")
    val triggerJson: String,
    @ColumnInfo(name = "condition_json")
    val conditionJson: String? = null,
    @ColumnInfo(name = "action_json")
    val actionJson: String,
    @ColumnInfo(name = "last_fired_at")
    val lastFiredAt: Long? = null,
    @ColumnInfo(name = "fire_count", defaultValue = "0")
    val fireCount: Int = 0,
    @ColumnInfo(name = "daily_fire_count", defaultValue = "0")
    val dailyFireCount: Int = 0,
    @ColumnInfo(name = "daily_fire_count_date")
    val dailyFireCountDate: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
