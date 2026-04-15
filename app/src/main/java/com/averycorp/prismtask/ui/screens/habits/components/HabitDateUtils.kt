package com.averycorp.prismtask.ui.screens.habits.components

import java.util.Calendar
import java.util.TimeZone

/**
 * Material 3 DatePicker returns UTC midnight millis for the selected date. Convert that to
 * midnight in the system default timezone so callers storing it as "day start" get the same
 * calendar day the user tapped — otherwise users in timezones behind UTC see the previous day.
 */
internal fun utcMillisToLocalDayStart(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = utcMillis
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)
    val localCal = Calendar.getInstance()
    localCal.set(year, month, day, 0, 0, 0)
    localCal.set(Calendar.MILLISECOND, 0)
    return localCal.timeInMillis
}

/**
 * Inverse of [utcMillisToLocalDayStart]: converts a local-midnight millis value into the
 * UTC-midnight millis that Material 3 DatePicker expects for pre-selection.
 */
internal fun localDayStartToUtcMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance()
    localCal.timeInMillis = localMillis
    val year = localCal.get(Calendar.YEAR)
    val month = localCal.get(Calendar.MONTH)
    val day = localCal.get(Calendar.DAY_OF_MONTH)
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.set(year, month, day, 0, 0, 0)
    utcCal.set(Calendar.MILLISECOND, 0)
    return utcCal.timeInMillis
}
