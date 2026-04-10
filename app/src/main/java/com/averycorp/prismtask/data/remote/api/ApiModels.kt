package com.averycorp.prismtask.data.remote.api

import com.google.gson.annotations.SerializedName

/**
 * Data models exchanged with the PrismTask FastAPI backend.
 *
 * Field names use snake_case to match the backend JSON contract; Kotlin
 * property names use camelCase via [SerializedName].
 */

// region Auth

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String
)

// endregion

// region Tasks

data class ParseRequest(
    val text: String
)

data class ParsedTaskResponse(
    val title: String,
    @SerializedName("project_suggestion") val projectSuggestion: String?,
    @SerializedName("tag_suggestions") val tagSuggestions: List<String>?,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("due_time") val dueTime: String?,
    val priority: Int?,
    @SerializedName("recurrence_hint") val recurrenceHint: String?,
    val confidence: Double?
)

// endregion

// region App version

data class VersionResponse(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("release_notes") val releaseNotes: String?,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("apk_size_bytes") val apkSizeBytes: Long,
    val sha256: String?,
    @SerializedName("is_mandatory") val isMandatory: Boolean
)

// endregion

// region AI Productivity

data class EisenhowerRequest(
    @SerializedName("task_ids") val taskIds: List<Long>? = null
)

data class EisenhowerCategorization(
    @SerializedName("task_id") val taskId: Long,
    val quadrant: String,
    val reason: String
)

data class EisenhowerSummary(
    @SerializedName("Q1") val q1: Int = 0,
    @SerializedName("Q2") val q2: Int = 0,
    @SerializedName("Q3") val q3: Int = 0,
    @SerializedName("Q4") val q4: Int = 0
)

data class EisenhowerResponse(
    val categorizations: List<EisenhowerCategorization>,
    val summary: EisenhowerSummary
)

data class PomodoroRequest(
    @SerializedName("available_minutes") val availableMinutes: Int = 120,
    @SerializedName("session_length") val sessionLength: Int = 25,
    @SerializedName("break_length") val breakLength: Int = 5,
    @SerializedName("long_break_length") val longBreakLength: Int = 15,
    @SerializedName("focus_preference") val focusPreference: String = "balanced"
)

data class SessionTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    @SerializedName("allocated_minutes") val allocatedMinutes: Int
)

data class PomodoroSessionResponse(
    @SerializedName("session_number") val sessionNumber: Int,
    val tasks: List<SessionTaskResponse>,
    val rationale: String
)

data class SkippedTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val reason: String
)

data class PomodoroResponse(
    val sessions: List<PomodoroSessionResponse>,
    @SerializedName("total_sessions") val totalSessions: Int,
    @SerializedName("total_work_minutes") val totalWorkMinutes: Int,
    @SerializedName("total_break_minutes") val totalBreakMinutes: Int,
    @SerializedName("skipped_tasks") val skippedTasks: List<SkippedTaskResponse> = emptyList()
)

// endregion

// region Export / Import

data class ImportResponse(
    @SerializedName("tasks_imported") val tasksImported: Int,
    @SerializedName("projects_imported") val projectsImported: Int,
    @SerializedName("tags_imported") val tagsImported: Int,
    @SerializedName("habits_imported") val habitsImported: Int,
    val mode: String
)

// endregion
