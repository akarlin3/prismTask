package com.averykarlin.averytask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["local_id", "entity_type"],
    indices = [Index("pending_action")]
)
data class SyncMetadataEntity(
    @ColumnInfo(name = "local_id")
    val localId: Long,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "cloud_id")
    val cloudId: String = "",

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = 0,

    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0,

    @ColumnInfo(name = "pending_action")
    val pendingAction: String? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)
