package com.averycorp.averytask.data.remote.sync

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Data models exchanged with the FastAPI backend sync endpoints
 * (`/api/v1/sync/push` and `/api/v1/sync/pull`).
 *
 * The payload uses generic JSON objects for entity data so the sync layer
 * stays decoupled from Room entity definitions.
 *
 * Entity types (string): "task", "project", "tag", "habit", "habit_completion"
 * Operations (string): "create", "update", "delete"
 */

// region Push

data class SyncOperation(
    @SerializedName("entity_type") val entityType: String,
    val operation: String,
    @SerializedName("client_id") val clientId: Long,
    @SerializedName("server_id") val serverId: String? = null,
    @SerializedName("updated_at") val updatedAt: Long,
    val data: JsonObject? = null
)

data class SyncPushRequest(
    val operations: List<SyncOperation>
)

data class SyncPushResult(
    @SerializedName("client_id") val clientId: Long,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("server_id") val serverId: String?,
    val status: String,
    val error: String? = null
)

data class SyncPushResponse(
    val results: List<SyncPushResult> = emptyList(),
    @SerializedName("server_timestamp") val serverTimestamp: Long = 0L
)

// endregion

// region Pull

data class SyncEntity(
    @SerializedName("server_id") val serverId: String?,
    @SerializedName("client_id") val clientId: Long?,
    @SerializedName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val data: JsonObject? = null
)

data class SyncPullResponse(
    val tasks: List<SyncEntity> = emptyList(),
    val projects: List<SyncEntity> = emptyList(),
    val tags: List<SyncEntity> = emptyList(),
    val habits: List<SyncEntity> = emptyList(),
    @SerializedName("habit_completions") val habitCompletions: List<SyncEntity> = emptyList(),
    @SerializedName("server_timestamp") val serverTimestamp: Long = 0L
)

// endregion
