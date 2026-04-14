package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
