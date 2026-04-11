package com.averycorp.prismtask.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * Action callbacks invoked by the Glance widgets. Each callback mutates
 * the local database through [WidgetDataProvider] and then nudges the
 * relevant widget to refresh so the UI reflects the new state.
 */
object WidgetActionKeys {
    val TASK_ID: ActionParameters.Key<Long> =
        ActionParameters.Key("prismtask-widget-task-id")
    val HABIT_ID: ActionParameters.Key<Long> =
        ActionParameters.Key("prismtask-widget-habit-id")
}

/** Toggles a task's completion state from a widget checkbox tap. */
class ToggleTaskFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[WidgetActionKeys.TASK_ID] ?: return
        try {
            WidgetDataProvider.toggleTaskCompletion(context, taskId)
        } catch (_: Exception) { /* fail silently — widget will redraw next tick */ }
        // Refresh every widget that might show this task.
        TodayWidget().updateAll(context)
        try { UpcomingWidget().updateAll(context) } catch (_: Exception) {}
        try { ProductivityWidget().updateAll(context) } catch (_: Exception) {}
    }
}

/** Toggles a habit's completion for today from a widget cell tap. */
class ToggleHabitFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[WidgetActionKeys.HABIT_ID] ?: return
        try {
            WidgetDataProvider.toggleHabitCompletion(context, habitId)
        } catch (_: Exception) { }
        HabitStreakWidget().updateAll(context)
        try { TodayWidget().updateAll(context) } catch (_: Exception) {}
        try { ProductivityWidget().updateAll(context) } catch (_: Exception) {}
    }
}

/** Helper for call sites that need to build a parameter bundle inline. */
fun taskIdParams(taskId: Long): ActionParameters =
    actionParametersOf(WidgetActionKeys.TASK_ID to taskId)

fun habitIdParams(habitId: Long): ActionParameters =
    actionParametersOf(WidgetActionKeys.HABIT_ID to habitId)
