package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habits",
    indices = [Index(value = ["cloud_id"], unique = true)]
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "target_frequency")
    val targetFrequency: Int = 1,
    @ColumnInfo(name = "frequency_period")
    val frequencyPeriod: String = "daily",
    @ColumnInfo(name = "active_days")
    val activeDays: String? = null,
    val color: String = "#4A90D9",
    val icon: String = "\u2B50",
    @ColumnInfo(name = "reminder_time")
    val reminderTime: Long? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    val category: String? = null,
    @ColumnInfo(name = "create_daily_task")
    val createDailyTask: Boolean = false,
    @ColumnInfo(name = "reminder_interval_millis")
    val reminderIntervalMillis: Long? = null,
    @ColumnInfo(name = "reminder_times_per_day", defaultValue = "1")
    val reminderTimesPerDay: Int = 1,
    @ColumnInfo(name = "has_logging", defaultValue = "0")
    val hasLogging: Boolean = false,
    @ColumnInfo(name = "track_booking", defaultValue = "0")
    val trackBooking: Boolean = false,
    @ColumnInfo(name = "track_previous_period", defaultValue = "0")
    val trackPreviousPeriod: Boolean = false,
    @ColumnInfo(name = "is_bookable", defaultValue = "0")
    val isBookable: Boolean = false,
    @ColumnInfo(name = "is_booked", defaultValue = "0")
    val isBooked: Boolean = false,
    @ColumnInfo(name = "booked_date")
    val bookedDate: Long? = null,
    @ColumnInfo(name = "booked_note")
    val bookedNote: String? = null,
    @ColumnInfo(name = "show_streak", defaultValue = "0")
    val showStreak: Boolean = false,
    @ColumnInfo(name = "nag_suppression_override_enabled", defaultValue = "0")
    val nagSuppressionOverrideEnabled: Boolean = false,
    @ColumnInfo(name = "nag_suppression_days_override", defaultValue = "-1")
    val nagSuppressionDaysOverride: Int = -1,
    /**
     * Per-habit override for the Today-screen "skip if completed within N days"
     * window. -1 = inherit the global default; 0 = explicitly disabled for this
     * habit; >=1 = use this many days as the window. See
     * [com.averycorp.prismtask.domain.usecase.HabitTodayVisibilityResolver].
     */
    @ColumnInfo(name = "today_skip_after_complete_days", defaultValue = "-1")
    val todaySkipAfterCompleteDays: Int = -1,
    /**
     * Per-habit override for the Today-screen "skip if next scheduled
     * occurrence is within N days" window. -1 = inherit the global default;
     * 0 = explicitly disabled for this habit; >=1 = use this many days.
     */
    @ColumnInfo(name = "today_skip_before_schedule_days", defaultValue = "-1")
    val todaySkipBeforeScheduleDays: Int = -1,
    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "template_key")
    val templateKey: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
