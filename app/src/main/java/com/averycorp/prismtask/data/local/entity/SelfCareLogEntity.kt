package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "self_care_logs",
    indices = [
        Index(value = ["routine_type", "date"], unique = true),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class SelfCareLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "routine_type")
    val routineType: String,
    val date: Long,
    @ColumnInfo(name = "selected_tier")
    val selectedTier: String = "solid",
    @ColumnInfo(name = "completed_steps")
    val completedSteps: String = "[]",
    @ColumnInfo(name = "tiers_by_time")
    val tiersByTime: String = "{}",
    @ColumnInfo(name = "is_complete")
    val isComplete: Boolean = false,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)
