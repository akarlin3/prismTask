package com.averykarlin.averytask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_sync",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CalendarSyncEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @ColumnInfo(name = "calendar_event_id")
    val calendarEventId: String,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_synced_version")
    val lastSyncedVersion: Long = 0
)
