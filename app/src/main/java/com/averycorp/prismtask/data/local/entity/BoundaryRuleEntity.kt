package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted boundary rule row (v1.4.0 V3).
 *
 * The runtime model is [com.averycorp.prismtask.domain.model.BoundaryRule].
 * This entity serializes time values as "HH:mm" strings and the active-day
 * set as a CSV of [java.time.DayOfWeek.getValue] ordinals so Room doesn't
 * need a TypeConverter.
 */
@Entity(tableName = "boundary_rules")
data class BoundaryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "rule_type")
    val ruleType: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "start_time")
    val startTime: String,
    @ColumnInfo(name = "end_time")
    val endTime: String,
    @ColumnInfo(name = "active_days_csv")
    val activeDaysCsv: String,
    @ColumnInfo(name = "is_enabled", defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
