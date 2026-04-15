package com.averycorp.prismtask.ui.screens.tasklist.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.averycorp.prismtask.data.local.entity.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Accent color for "Today" labels — shared between [GroupHeader] and
 * [formatDueDate]. Matches the orange used across the Today screen.
 */
internal val TodayOrange = Color(0xFFE8872A)

/**
 * A due-date label together with the color it should render in.
 * Overdue dates keep the normal on-surface-variant color; "Today"
 * picks up [TodayOrange] for the quick visual scan.
 */
internal data class DueDateLabel(
    val text: String,
    val color: Color
)

/**
 * Format a task's due date into its card-row label: "Today" for
 * today, "Tomorrow" for tomorrow, formatted date otherwise.
 */
@Composable
internal fun formatDueDate(epochMillis: Long): DueDateLabel {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfTomorrow = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfDayAfter = cal.timeInMillis

    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    return when {
        epochMillis < startOfToday -> {
            val formatted = dateFmt.format(Date(epochMillis))
            DueDateLabel(formatted, normal)
        }
        epochMillis < startOfTomorrow -> DueDateLabel("Today", TodayOrange)
        epochMillis < startOfDayAfter -> DueDateLabel("Tomorrow", normal)
        else -> DueDateLabel(dateFmt.format(Date(epochMillis)), normal)
    }
}

/**
 * Returns true if the task is not yet completed and its due date is
 * strictly before the start of today.
 */
internal fun isTaskOverdue(task: TaskEntity): Boolean {
    if (task.isCompleted || task.dueDate == null) return false
    val startOfToday = Calendar
        .getInstance()
        .apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    return task.dueDate < startOfToday
}
