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

// region AI Daily Briefing

data class DailyBriefingRequest(
    val date: String? = null
)

data class BriefingPriorityResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    val reason: String
)

data class SuggestedTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    @SerializedName("suggested_time") val suggestedTime: String,
    val reason: String
)

data class DailyBriefingResponse(
    val greeting: String,
    @SerializedName("top_priorities") val topPriorities: List<BriefingPriorityResponse>,
    @SerializedName("heads_up") val headsUp: List<String> = emptyList(),
    @SerializedName("suggested_order") val suggestedOrder: List<SuggestedTaskResponse>,
    @SerializedName("habit_reminders") val habitReminders: List<String> = emptyList(),
    @SerializedName("day_type") val dayType: String
)

// endregion

// region AI Weekly Plan

data class WeeklyPlanPreferencesRequest(
    @SerializedName("work_days") val workDays: List<String> = listOf("MO", "TU", "WE", "TH", "FR"),
    @SerializedName("focus_hours_per_day") val focusHoursPerDay: Int = 6,
    @SerializedName("prefer_front_loading") val preferFrontLoading: Boolean = true
)

data class WeeklyPlanRequest(
    @SerializedName("week_start") val weekStart: String? = null,
    val preferences: WeeklyPlanPreferencesRequest = WeeklyPlanPreferencesRequest()
)

data class PlannedTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    @SerializedName("suggested_time") val suggestedTime: String,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    val reason: String
)

data class DayPlanResponse(
    val date: String,
    val tasks: List<PlannedTaskResponse>,
    @SerializedName("total_hours") val totalHours: Double,
    @SerializedName("calendar_events") val calendarEvents: List<String> = emptyList(),
    val habits: List<String> = emptyList()
)

data class UnscheduledTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    val reason: String
)

data class WeeklyPlanResponse(
    val plan: Map<String, DayPlanResponse>,
    val unscheduled: List<UnscheduledTaskResponse> = emptyList(),
    @SerializedName("week_summary") val weekSummary: String,
    val tips: List<String> = emptyList()
)

// endregion

// region AI Time Block

data class TimeBlockRequest(
    val date: String? = null,
    @SerializedName("day_start") val dayStart: String = "09:00",
    @SerializedName("day_end") val dayEnd: String = "18:00",
    @SerializedName("block_size_minutes") val blockSizeMinutes: Int = 30,
    @SerializedName("include_breaks") val includeBreaks: Boolean = true,
    @SerializedName("break_frequency_minutes") val breakFrequencyMinutes: Int = 90,
    @SerializedName("break_duration_minutes") val breakDurationMinutes: Int = 15
)

data class ScheduleBlockResponse(
    val start: String,
    val end: String,
    val type: String,
    @SerializedName("task_id") val taskId: Long?,
    val title: String,
    val reason: String
)

data class TimeBlockStatsResponse(
    @SerializedName("total_work_minutes") val totalWorkMinutes: Int,
    @SerializedName("total_break_minutes") val totalBreakMinutes: Int,
    @SerializedName("total_free_minutes") val totalFreeMinutes: Int,
    @SerializedName("tasks_scheduled") val tasksScheduled: Int,
    @SerializedName("tasks_deferred") val tasksDeferred: Int
)

data class TimeBlockResponse(
    val schedule: List<ScheduleBlockResponse>,
    @SerializedName("unscheduled_tasks") val unscheduledTasks: List<UnscheduledTaskResponse> = emptyList(),
    val stats: TimeBlockStatsResponse
)

// endregion

// region AI Chat

data class ChatRequest(
    val message: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("task_context_id") val taskContextId: Long? = null,
    val tier: String = "PREMIUM"
)

data class ChatActionResponse(
    val type: String,
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("task_ids") val taskIds: List<String>? = null,
    val to: String? = null,
    val subtasks: List<String>? = null,
    val minutes: Int? = null,
    val title: String? = null,
    val due: String? = null,
    val priority: String? = null
)

data class ChatTokensUsed(
    val input: Int,
    val output: Int
)

data class ChatResponse(
    val message: String,
    val actions: List<ChatActionResponse> = emptyList(),
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("tokens_used") val tokensUsed: ChatTokensUsed? = null
)

// region AI Evening Summary

data class EveningSummaryRequest(
    @SerializedName("completed_tasks") val completedTasks: List<String>,
    @SerializedName("remaining_count") val remainingCount: Int,
    @SerializedName("habits_done") val habitsDone: Int,
    @SerializedName("habits_total") val habitsTotal: Int,
    @SerializedName("completed_overdue") val completedOverdue: Boolean,
    @SerializedName("completed_stalled") val completedStalled: Boolean
)

data class EveningSummaryResponse(
    val summary: String
)

// endregion

// region AI Re-engagement Nudge

data class ReengagementRequest(
    @SerializedName("days_absent") val daysAbsent: Int,
    @SerializedName("last_task_title") val lastTaskTitle: String?,
    @SerializedName("total_pending") val totalPending: Int
)

data class ReengagementResponse(
    val nudge: String
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
