package com.averycorp.averytask.data.remote.api

import com.google.gson.annotations.SerializedName

/**
 * Data models exchanged with the AveryTask FastAPI backend.
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

// region Export / Import

data class ImportResponse(
    @SerializedName("tasks_imported") val tasksImported: Int,
    @SerializedName("projects_imported") val projectsImported: Int,
    @SerializedName("tags_imported") val tagsImported: Int,
    @SerializedName("habits_imported") val habitsImported: Int,
    val mode: String
)

// endregion
