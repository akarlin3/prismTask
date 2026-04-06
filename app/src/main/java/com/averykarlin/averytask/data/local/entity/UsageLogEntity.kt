package com.averykarlin.averytask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_logs")
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: Long? = null,

    @ColumnInfo(name = "entity_name")
    val entityName: String? = null,

    @ColumnInfo(name = "task_title")
    val taskTitle: String,

    @ColumnInfo(name = "title_keywords")
    val titleKeywords: String,

    val timestamp: Long = System.currentTimeMillis()
)
