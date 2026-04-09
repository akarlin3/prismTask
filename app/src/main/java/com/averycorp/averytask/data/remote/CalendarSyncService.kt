package com.averycorp.averytask.data.remote

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.averycorp.averytask.data.local.dao.CalendarSyncDao
import com.averycorp.averytask.data.local.entity.CalendarSyncEntity
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.preferences.CalendarPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCalendar(val id: Long, val name: String, val accountName: String)

@Singleton
class CalendarSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarSyncDao: CalendarSyncDao,
    private val calendarPreferences: CalendarPreferences
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    fun getAvailableCalendars(): List<DeviceCalendar> {
        val calendars = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, selection, selectionArgs, null
            )
            cursor?.let {
                while (it.moveToNext()) {
                    calendars.add(
                        DeviceCalendar(
                            id = it.getLong(0),
                            name = it.getString(1) ?: "Unknown",
                            accountName = it.getString(2) ?: ""
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Calendar permission denied", e)
        } finally {
            cursor?.close()
        }
        return calendars
    }

    suspend fun syncTaskToCalendar(task: TaskEntity) {
        if (!calendarPreferences.isEnabled().first()) return
        val calendarId = calendarPreferences.getCalendarId().first()
        if (calendarId < 0) return
        if (task.dueDate == null) {
            // No due date — remove any existing event
            removeEventForTask(task.id)
            return
        }
        if (task.isCompleted) {
            removeEventForTask(task.id)
            return
        }

        val existingEventId = calendarSyncDao.getCalendarEventId(task.id)
        if (existingEventId != null) {
            updateCalendarEvent(existingEventId, task, calendarId)
        } else {
            createCalendarEvent(task, calendarId)
        }
    }

    suspend fun removeEventForTask(taskId: Long) {
        val eventId = calendarSyncDao.getCalendarEventId(taskId) ?: return
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete calendar event", e)
        }
        calendarSyncDao.deleteByTaskId(taskId)
    }

    private suspend fun createCalendarEvent(task: TaskEntity, calendarId: Long) {
        val values = buildEventValues(task, calendarId)
        try {
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment
            if (eventId != null) {
                calendarSyncDao.upsert(
                    CalendarSyncEntity(
                        taskId = task.id,
                        calendarEventId = eventId,
                        lastSyncedAt = System.currentTimeMillis(),
                        lastSyncedVersion = task.updatedAt
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create calendar event for task ${task.id}", e)
        }
    }

    private suspend fun updateCalendarEvent(eventId: String, task: TaskEntity, calendarId: Long) {
        val values = buildEventValues(task, calendarId)
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
            contentResolver.update(uri, values, null, null)
            calendarSyncDao.upsert(
                CalendarSyncEntity(
                    taskId = task.id,
                    calendarEventId = eventId,
                    lastSyncedAt = System.currentTimeMillis(),
                    lastSyncedVersion = task.updatedAt
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update calendar event $eventId", e)
            // Event might have been deleted externally — recreate
            calendarSyncDao.deleteByTaskId(task.id)
            createCalendarEvent(task, calendarId)
        }
    }

    private fun buildEventValues(task: TaskEntity, calendarId: Long): ContentValues {
        val startMillis = if (task.scheduledStartTime != null && task.scheduledStartTime > 0) {
            task.scheduledStartTime
        } else if (task.dueTime != null && task.dueTime > 0) {
            task.dueDate!! + task.dueTime
        } else {
            task.dueDate!!
        }

        val durationMinutes = task.estimatedDuration ?: 60
        val endMillis = startMillis + durationMinutes * 60_000L

        val isAllDay = task.dueTime == null && task.scheduledStartTime == null

        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, task.title)
            put(CalendarContract.Events.DESCRIPTION, buildDescription(task))
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (isAllDay) {
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.DTSTART, task.dueDate!!)
                put(CalendarContract.Events.DTEND, task.dueDate!! + 86_400_000L)
            } else {
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
            }
        }
    }

    private fun buildDescription(task: TaskEntity): String {
        val parts = mutableListOf<String>()
        if (!task.description.isNullOrBlank()) parts.add(task.description)
        if (!task.notes.isNullOrBlank()) parts.add("Notes: ${task.notes}")
        val priorityLabel = when (task.priority) {
            1 -> "Low"; 2 -> "Medium"; 3 -> "High"; 4 -> "Urgent"; else -> null
        }
        if (priorityLabel != null) parts.add("Priority: $priorityLabel")
        parts.add("[PrismTask]")
        return parts.joinToString("\n")
    }

    suspend fun fullCalendarSync(tasks: List<TaskEntity>) {
        if (!calendarPreferences.isEnabled().first()) return
        val calendarId = calendarPreferences.getCalendarId().first()
        if (calendarId < 0) return

        for (task in tasks) {
            if (task.dueDate != null && !task.isCompleted) {
                syncTaskToCalendar(task)
            } else {
                removeEventForTask(task.id)
            }
        }
    }

    suspend fun clearAllEvents() {
        // Remove all calendar events we've synced
        val projection = arrayOf("task_id", "calendar_event_id")
        // Just iterate through what we have in our sync table
        val tasks = mutableListOf<Long>()
        // Use DAO to get all then delete
        // Simple approach: delete all via DAO
        calendarSyncDao.deleteAll()
    }

    companion object {
        private const val TAG = "CalendarSyncService"
    }
}
