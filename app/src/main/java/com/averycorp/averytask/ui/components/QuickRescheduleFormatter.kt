package com.averycorp.averytask.ui.components

import com.averycorp.averytask.domain.usecase.DateShortcuts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats dates for the quick-reschedule snackbar. Extracted so ViewModels can
 * depend on it without pulling in any Compose types.
 */
object QuickRescheduleFormatter {
    private val formatter = SimpleDateFormat("MMM d", Locale.getDefault())

    /**
     * Returns a short human-readable label for a due date, using "Today" /
     * "Tomorrow" / "No Date" where appropriate and falling back to "MMM d".
     */
    fun describe(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return "No Date"
        val today = DateShortcuts.today(now)
        val tomorrow = DateShortcuts.tomorrow(now)
        val normalized = DateShortcuts.startOfDay(millis)
        return when (normalized) {
            today -> "Today"
            tomorrow -> "Tomorrow"
            else -> formatter.format(Date(millis))
        }
    }
}
