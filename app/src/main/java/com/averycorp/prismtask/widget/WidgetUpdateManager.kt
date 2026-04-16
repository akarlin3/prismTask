package com.averycorp.prismtask.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Glance widget refreshes across all 7 widget types.
 *
 * Each `update*()` method is debounced: when called multiple times within
 * [DEBOUNCE_MILLIS] (e.g. during batch task operations) the actual refresh
 * is coalesced into a single update, saving battery and avoiding flicker.
 */
@Singleton
class WidgetUpdateManager
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob())

    private var allWidgetsJob: Job? = null
    private var taskWidgetsJob: Job? = null
    private var habitWidgetsJob: Job? = null
    private var timerWidgetJob: Job? = null
    private var productivityWidgetJob: Job? = null

    /** Refreshes all 7 widgets (debounced). */
    suspend fun updateAllWidgets() {
        allWidgetsJob?.cancel()
        allWidgetsJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            safeUpdate { TodayWidget().updateAll(context) }
            safeUpdate { HabitStreakWidget().updateAll(context) }
            safeUpdate { QuickAddWidget().updateAll(context) }
            safeUpdate { CalendarWidget().updateAll(context) }
            safeUpdate { ProductivityWidget().updateAll(context) }
            safeUpdate { TimerWidget().updateAll(context) }
            safeUpdate { UpcomingWidget().updateAll(context) }
        }
    }

    /** Refreshes task-related widgets: Today, Upcoming, Calendar, Productivity (debounced). */
    suspend fun updateTaskWidgets() {
        taskWidgetsJob?.cancel()
        taskWidgetsJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            safeUpdate { TodayWidget().updateAll(context) }
            safeUpdate { UpcomingWidget().updateAll(context) }
            safeUpdate { CalendarWidget().updateAll(context) }
            safeUpdate { ProductivityWidget().updateAll(context) }
        }
    }

    /** Refreshes habit-related widgets: HabitStreak + Today (habits appear on Today) (debounced). */
    suspend fun updateHabitWidgets() {
        habitWidgetsJob?.cancel()
        habitWidgetsJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            safeUpdate { HabitStreakWidget().updateAll(context) }
            safeUpdate { TodayWidget().updateAll(context) }
        }
    }

    /** Refreshes the TimerWidget only (debounced). */
    suspend fun updateTimerWidget() {
        timerWidgetJob?.cancel()
        timerWidgetJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            safeUpdate { TimerWidget().updateAll(context) }
        }
    }

    /** Refreshes the ProductivityWidget only (debounced). */
    suspend fun updateProductivityWidget() {
        productivityWidgetJob?.cancel()
        productivityWidgetJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            safeUpdate { ProductivityWidget().updateAll(context) }
        }
    }

    /** Legacy aliases for backward compat with existing callers. */
    suspend fun updateTodayWidget() {
        safeUpdate { TodayWidget().updateAll(context) }
    }

    suspend fun updateHabitWidget() {
        safeUpdate { HabitStreakWidget().updateAll(context) }
    }

    private suspend fun safeUpdate(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Widget update failed (widget may not be placed): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateManager"

        /** Debounce window: rapid calls within this period are coalesced. */
        const val DEBOUNCE_MILLIS = 500L

        /**
         * Refreshes habit-related widgets from a Glance ActionCallback
         * where Hilt injection is unavailable. Mirrors [updateHabitWidgets]
         * but runs synchronously without debounce.
         */
        suspend fun refreshHabitWidgets(context: Context) {
            try {
                HabitStreakWidget().updateAll(context)
            } catch (e: Exception) {
                Log.w(TAG, "HabitStreakWidget refresh failed: ${e.message}")
            }
            try {
                TodayWidget().updateAll(context)
            } catch (e: Exception) {
                Log.w(TAG, "TodayWidget refresh failed: ${e.message}")
            }
            try {
                ProductivityWidget().updateAll(context)
            } catch (e: Exception) {
                Log.w(TAG, "ProductivityWidget refresh failed: ${e.message}")
            }
        }
    }
}
