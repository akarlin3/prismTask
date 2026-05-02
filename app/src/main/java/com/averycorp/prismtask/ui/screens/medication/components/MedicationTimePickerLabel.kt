package com.averycorp.prismtask.ui.screens.medication.components

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Source-of-truth readout for the time the user has currently picked in a
 * medication `TimePicker`. The Material 3 dial + AM/PM toggle can mislead
 * users about the selected half-of-day (see
 * `MEDICATION_TIME_PICKER_DISPLAY_DRIFT_AUDIT.md` § I1); this helper
 * renders the underlying `state.hour` / `state.minute` directly so the
 * label cannot diverge from what the save path will actually persist.
 *
 * Pattern is locale-aware: `HH:mm` (24-hour) when the device prefers
 * 24-hour clocks, `h:mm a` otherwise. Pure function so it composes
 * cheaply test-side.
 */
internal fun formatPickedTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault()
): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute)
        .format(DateTimeFormatter.ofPattern(pattern, locale))
}
