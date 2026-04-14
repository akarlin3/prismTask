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

data class FirebaseTokenRequest(
    @SerializedName("firebase_token") val firebaseToken: String,
    val name: String? = null
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class UserInfoResponse(
    val id: Int,
    val email: String,
    val name: String,
    val tier: String = "FREE",
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("effective_tier") val effectiveTier: String = "FREE"
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

// region AI Coaching

data class CoachingTaskSummary(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    val priority: Int,
    @SerializedName("estimated_minutes") val estimatedMinutes: Int? = null
)

data class CoachingContext(
    // Task-scoped fields
    @SerializedName("task_title") val taskTitle: String? = null,
    @SerializedName("task_description") val taskDescription: String? = null,
    @SerializedName("days_since_creation") val daysSinceCreation: Int? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    val priority: Int? = null,
    @SerializedName("subtask_count") val subtaskCount: Int? = null,
    @SerializedName("completed_subtasks") val completedSubtasks: Int? = null,

    // Perfectionism-scoped fields
    @SerializedName("edit_count") val editCount: Int? = null,
    @SerializedName("reschedule_count") val rescheduleCount: Int? = null,
    @SerializedName("subtasks_added") val subtasksAdded: Int? = null,
    @SerializedName("subtasks_completed") val subtasksCompleted: Int? = null,
    val reason: String? = null,

    // Energy plan fields
    @SerializedName("energy_level") val energyLevel: String? = null,
    @SerializedName("tasks_due_today") val tasksDueToday: List<CoachingTaskSummary>? = null,
    @SerializedName("overdue_count") val overdueCount: Int? = null,
    @SerializedName("yesterday_completed") val yesterdayCompleted: Int? = null,
    @SerializedName("yesterday_total") val yesterdayTotal: Int? = null,

    // Welcome back fields
    @SerializedName("days_absent") val daysAbsent: Int? = null,
    @SerializedName("recent_completions") val recentCompletions: Int? = null,

    // Celebration fields
    @SerializedName("completed_subtask_count") val completedSubtaskCount: Int? = null,
    @SerializedName("total_subtask_count") val totalSubtaskCount: Int? = null,
    @SerializedName("days_overdue") val daysOverdue: Int? = null,
    @SerializedName("first_after_gap") val firstAfterGap: Boolean? = null,

    // Breakdown fields
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("project_name") val projectName: String? = null
)

data class CoachingRequest(
    val trigger: String,
    @SerializedName("task_id") val taskId: Long? = null,
    val context: CoachingContext,
    val tier: String
)

data class CoachingResponse(
    val message: String? = null,
    val subtasks: List<String>? = null
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

data class BugReportMirrorResponse(
    val id: String,
    val status: String
)

// endregion

// region AI Import Parse

data class ParseImportRequest(
    val content: String
)

data class ParsedImportItemResponse(
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val priority: Int = 0,
    val completed: Boolean = false,
    val subtasks: List<ParsedImportItemResponse> = emptyList()
)

data class ParseImportResponse(
    val name: String? = null,
    val items: List<ParsedImportItemResponse>
)

// endregion

// region AI Checklist Parse

data class ParseChecklistRequest(
    val content: String
)

data class ParsedChecklistCourseResponse(
    val code: String,
    val name: String
)

data class ParsedChecklistProjectResponse(
    val name: String,
    val color: String,
    val icon: String
)

data class ParsedChecklistTagResponse(
    val name: String,
    val color: String? = null
)

data class ParsedChecklistTaskResponse(
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val priority: Int = 0,
    val completed: Boolean = false,
    val tags: List<String> = emptyList(),
    val estimatedMinutes: Int? = null,
    val subtasks: List<ParsedChecklistTaskResponse> = emptyList()
)

data class ParseChecklistResponse(
    val course: ParsedChecklistCourseResponse,
    val project: ParsedChecklistProjectResponse,
    val tags: List<ParsedChecklistTagResponse>,
    val tasks: List<ParsedChecklistTaskResponse>
)

// endregion
