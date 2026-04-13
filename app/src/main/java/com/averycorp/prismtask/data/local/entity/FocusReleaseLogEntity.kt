package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only analytics log for Focus & Release Mode events.
 * Tracks stuck-detection triggers, breaker choices, celebration fires, etc.
 * This data is NEVER sent to the backend — it is sensitive behavioral data.
 */
@Entity(tableName = "focus_release_logs")
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
    val createdAt: Long = System.currentTimeMillis()
)
