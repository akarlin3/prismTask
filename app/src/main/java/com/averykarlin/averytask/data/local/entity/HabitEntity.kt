package com.averykarlin.averytask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val description: String? = null,

    @ColumnInfo(name = "target_frequency")
    val targetFrequency: Int = 1,

    @ColumnInfo(name = "frequency_period")
    val frequencyPeriod: String = "daily",

    @ColumnInfo(name = "active_days")
    val activeDays: String? = null,

    val color: String = "#4A90D9",

    val icon: String = "\u2B50",

    @ColumnInfo(name = "reminder_time")
    val reminderTime: Long? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "create_daily_task")
    val createDailyTask: Boolean = false,

    val category: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
