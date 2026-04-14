package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A reusable habit blueprint.
 *
 * Added in v1.3.0 (P15).
 */
@Entity(tableName = "habit_templates")
data class HabitTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "icon_emoji")
    val iconEmoji: String? = null,
    val color: String? = null,
    val category: String? = null,
    val frequency: String = "DAILY",
    @ColumnInfo(name = "target_count", defaultValue = "1")
    val targetCount: Int = 1,
    /** CSV of day-of-week ints (1=Mon..7=Sun). Empty = every day. */
    @ColumnInfo(name = "active_days_csv")
    val activeDaysCsv: String = "",
    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "usage_count", defaultValue = "0")
    val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
