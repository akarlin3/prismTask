package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Analytics log for Focus & Release Mode events.
 * Tracks stuck-detection triggers, breaker choices, celebration fires, etc.
 *
 * v1.4.38: this data now syncs across the user's own devices via
 * Firestore (`users/{uid}/focus_release_logs`). It never leaves the
 * user's own Firebase project — no third-party analytics backend.
 */
@Entity(
    tableName = "focus_release_logs",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("task_id"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class FocusReleaseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Event type, e.g. "stuck_detected", "breaker_start_suggestion", "breaker_pick_for_me",
     * "breaker_dismiss", "show_all_options", "celebration_fired", "rework_warning_shown",
     * "rework_resisted", "good_enough_shipped", "revision_locked" */
    @ColumnInfo(name = "event_type")
    val eventType: String,
    /** Optional task ID associated with the event. */
    @ColumnInfo(name = "task_id")
    val taskId: Long? = null,
    /** Optional screen/context where the event occurred. */
    @ColumnInfo(name = "context")
    val context: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
