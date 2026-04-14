package com.averycorp.prismtask.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WidgetCalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val calendarColor: Int?
)

private sealed class TimelineItem(
    val sortTime: Long
) {
    class Task(
        val row: WidgetTaskRow
    ) : TimelineItem(row.dueDate ?: Long.MAX_VALUE)

    class Event(
        val event: WidgetCalendarEvent
    ) : TimelineItem(event.startTime)
}

class CalendarWidget : GlanceAppWidget() {
    companion object {
        private val MEDIUM = DpSize(250.dp, 170.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val upcoming = try {
            WidgetDataProvider.getUpcomingData(context)
        } catch (
            _: Exception
        ) {
            UpcomingWidgetData(emptyList(), emptyList(), emptyList(), emptyList())
        }
        val calendarEvents = try {
            getCalendarEventsForWidget(context)
        } catch (_: Exception) {
            emptyList()
        }
        provideContent {
            val size = LocalSize.current
            GlanceTheme { CalendarContent(context, upcoming, calendarEvents, size) }
        }
    }
}

private fun getCalendarEventsForWidget(context: Context): List<WidgetCalendarEvent> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val events = mutableListOf<WidgetCalendarEvent>()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val dayStart = cal.timeInMillis
    val dayEnd = dayStart + 86_400_000L
    val projection =
        arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR
        )
    val uri = CalendarContract.Instances.CONTENT_URI
        .buildUpon()
        .appendPath(dayStart.toString())
        .appendPath(dayEnd.toString())
        .build()
    try {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events.add(
                    WidgetCalendarEvent(
                        cursor.getString(0) ?: "(No Title)",
                        cursor.getLong(1),
                        cursor.getLong(2),
                        cursor.getInt(3) != 0,
                        cursor.getInt(4)
                    )
                )
            }
        }
    } catch (
        _: Exception
    ) {
    }
    return events
}

@Composable
private fun CalendarContent(context: Context, data: UpcomingWidgetData, calendarEvents: List<WidgetCalendarEvent>, size: DpSize) {
    val isLarge = size.width >= 350.dp
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val todayItems = buildMergedTimeline(data.today, calendarEvents)
    val hasCalendarPermission =
        calendarEvents.isNotEmpty() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Today's Schedule",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "${data.today.size} tasks, ${calendarEvents.size} events",
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (todayItems.isEmpty() && !hasCalendarPermission) {
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(openAppIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "\uD83D\uDCC5", style = TextStyle(fontSize = 20.sp))
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = "Connect Calendar in Settings", style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.primary))
                }
            }
        } else if (todayItems.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Clear Schedule Today \u2728", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        } else if (isLarge) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Today",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    todayItems.take(5).forEach { item ->
                        TimelineRow(item, timeFormat, context)
                        Spacer(modifier = GlanceModifier.height(2.dp))
                    }
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Tomorrow",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    if (data.tomorrow.isEmpty()) {
                        Text(text = "\u2014", style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        data.tomorrow.take(5).forEach { row ->
                            TaskTimelineRow(row, timeFormat, context)
                            Spacer(modifier = GlanceModifier.height(2.dp))
                        }
                    }
                }
            }
        } else {
            todayItems.take(6).forEach { item ->
                TimelineRow(item, timeFormat, context)
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
        }
    }
}

private fun buildMergedTimeline(tasks: List<WidgetTaskRow>, events: List<WidgetCalendarEvent>): List<TimelineItem> = (
    tasks.map { TimelineItem.Task(it) } +
        events.map { TimelineItem.Event(it) }
    ).sortedBy { it.sortTime }

@Composable
private fun TimelineRow(
    item: TimelineItem,
    timeFormat: SimpleDateFormat,
    context: Context
) {
    when (item) {
        is TimelineItem.Task -> TaskTimelineRow(item.row, timeFormat, context)
        is TimelineItem.Event -> EventTimelineRow(item.event, timeFormat)
    }
}

@Composable
private fun TaskTimelineRow(row: WidgetTaskRow, timeFormat: SimpleDateFormat, context: Context) {
    val taskIntent = Intent(context, MainActivity::class.java).apply {
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_task")
        putExtra("task_id", row.id)
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(actionStartActivity(taskIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.dueDate?.let {
                timeFormat.format(Date(it))
            } ?: "--:--",
            style = TextStyle(
                fontSize = 10.sp,
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            ),
            modifier = GlanceModifier.width(56.dp)
        )
        Box(modifier = GlanceModifier.size(6.dp).cornerRadius(3.dp).background(priorityColorFor(row.priority))) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(text = row.title, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
    }
}

@Composable
private fun EventTimelineRow(event: WidgetCalendarEvent, timeFormat: SimpleDateFormat) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (event.isAllDay) "All day" else timeFormat.format(Date(event.startTime)),
            style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.width(56.dp)
        )
        val eventColor = event.calendarColor?.let { ColorProvider(Color(it)) } ?: ColorProvider(Color(0xFF1976D2))
        Box(modifier = GlanceModifier.size(6.dp).cornerRadius(3.dp).background(eventColor)) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(text = event.title, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}
