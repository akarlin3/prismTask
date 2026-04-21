package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_templates",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateProjectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("templateProjectId"),
        Index("userId"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class TaskTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Firestore document ID (v52+). Populated by sync pull; unique index enforces
    // one local row per cloud doc. NULL for rows not yet synced.
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    // Firebase UID for sync
    val userId: String? = null,
    // Backend API numeric ID (distinct from cloudId above)
    val remoteId: Int? = null,
    val name: String,
    val description: String? = null,
    // emoji
    val icon: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "template_title")
    val templateTitle: String? = null,
    @ColumnInfo(name = "template_description")
    val templateDescription: String? = null,
    @ColumnInfo(name = "template_priority")
    val templatePriority: Int? = null,
    @ColumnInfo(name = "templateProjectId")
    val templateProjectId: Long? = null,
    // JSON array of tag IDs
    @ColumnInfo(name = "template_tags_json")
    val templateTagsJson: String? = null,
    @ColumnInfo(name = "template_recurrence_json")
    val templateRecurrenceJson: String? = null,
    // minutes
    @ColumnInfo(name = "template_duration")
    val templateDuration: Int? = null,
    // JSON array of subtask titles
    @ColumnInfo(name = "template_subtasks_json")
    val templateSubtasksJson: String? = null,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,
    /**
     * Stable identity for built-in templates (parity with [HabitEntity.templateKey]).
     * Populated by the built-in template seeder; the post-sync reconciler groups
     * duplicate built-ins by this key so rename drift doesn't fork them.
     * NULL for user-created templates.
     */
    @ColumnInfo(name = "template_key")
    val templateKey: String? = null,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
