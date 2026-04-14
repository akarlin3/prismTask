package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Date

/**
 * Centralized helper for scheduling exact alarms across the Android version
 * matrix. All alarm-based notification paths (task reminders, medication
 * reminders, self-care steps) route through this helper so reliability fixes
 * stay in one place.
 *
 * Permission model:
 *  - API < 31 (pre-S): exact alarms are always allowed, no permission required.
 *  - API 31-32 (S/S_V2): SCHEDULE_EXACT_ALARM is requested and the user must
 *    toggle it on via system Settings. MainActivity prompts on cold start.
 *  - API 33+ (Tiramisu+): USE_EXACT_ALARM is auto-granted for reminder-style
 *    apps (calendar/timer/reminder). canScheduleExactAlarms() returns true
 *    without any user interaction.
 *
 * If exact alarms are unavailable at runtime we fall back to
 * setAndAllowWhileIdle so reminders still fire (just less precisely)
 * rather than silently dropping them.
 */
object ExactAlarmHelper {
    private const val TAG = "ExactAlarmHelper"

    /**
     * Schedule [pendingIntent] to fire as close to [triggerTime] as the OS
     * allows, surviving Doze/App Standby. Falls back to inexact scheduling if
     * exact alarms are not permitted at runtime.
     */
    fun scheduleExact(context: Context, triggerTime: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        Log.d(
            TAG,
            "Scheduling alarm: triggerTime=${Date(triggerTime)}, " +
                "canExact=${canScheduleExact(context)}, " +
                "sdk=${Build.VERSION.SDK_INT}"
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                Log.w(
                    TAG,
                    "Exact alarms not permitted on API ${Build.VERSION.SDK_INT}; " +
                        "falling back to inexact alarm"
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied at scheduling time, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * Whether the app may schedule exact alarms right now. Always true on
     * API < 31; on API 31+ delegates to [AlarmManager.canScheduleExactAlarms].
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }
}
