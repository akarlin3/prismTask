package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.data.local.entity.HabitEntity

/**
 * Utility functions for habit notification suppression logic.
 *
 * When a recurring habit has a scheduled occurrence within a configurable
 * window, nag notifications are suppressed and replaced by a delayed
 * follow-up ("how did it go?") after the scheduled time.
 */
object HabitNotificationUtils {

    /**
     * Returns the effective suppression window in days for a given habit.
     * Returns 0 if suppression is disabled (either globally or per-habit).
     *
     * @param habit The habit to resolve suppression days for.
     * @param globalSuppressionDays The global default from NotificationPreferences.
     */
    fun resolveSuppressionDays(
        habit: HabitEntity,
        globalSuppressionDays: Int
    ): Int {
        if (!habit.nagSuppressionOverrideEnabled) return globalSuppressionDays
        return habit.nagSuppressionDaysOverride.takeIf { it >= 0 } ?: globalSuppressionDays
    }
}
