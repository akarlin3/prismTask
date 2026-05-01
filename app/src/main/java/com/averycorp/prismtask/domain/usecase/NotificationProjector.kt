package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.notifications.HabitReminderScheduler
import com.averycorp.prismtask.notifications.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single notification we expect to deliver in the future. Used by the
 * admin projected-notification log to surface what's queued up without
 * having to inspect AlarmManager (which Android does not expose to apps).
 */
data class ProjectedNotification(
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
    val source: Source,
    val sourceId: Long?
) {
    enum class Source(val label: String) {
        TASK_REMINDER("Task Reminder"),
        HABIT_DAILY("Habit Reminder")
    }
}

/**
 * Computes the next N notifications the app expects to fire, derived
 * from the same scheduling logic the real schedulers use. Mirrors
 * [ReminderScheduler.computeEffectiveTrigger] for tasks and
 * [HabitReminderScheduler.computeNextDailyTrigger] for habits with a
 * fixed daily reminder time, so the log stays honest as that logic
 * evolves.
 *
 * Sources currently covered: task reminders and habit daily-time
 * reminders. Habit-interval, medication, escalation, and worker-driven
 * notifications (briefing, evening summary, weekly summaries) are not
 * yet projected — see the screen header for the documented gap.
 */
@Singleton
class NotificationProjector @Inject constructor(
    private val taskDao: TaskDao,
    private val habitDao: HabitDao
) {
    suspend fun projectNext(
        limit: Int = 10,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ProjectedNotification> {
        val candidates = projectTasks(nowMillis) + projectHabits(nowMillis)
        return candidates
            .filter { it.triggerAtMillis > nowMillis }
            .sortedBy { it.triggerAtMillis }
            .take(limit)
    }

    private suspend fun projectTasks(now: Long): List<ProjectedNotification> {
        return taskDao.getIncompleteTasksWithReminders().mapNotNull { task ->
            val dueDate = task.dueDate ?: return@mapNotNull null
            val offset = task.reminderOffset ?: return@mapNotNull null
            val effectiveDue = ReminderScheduler.combineDateAndTime(dueDate, task.dueTime)
            val rawTrigger = ReminderScheduler.computeTriggerTime(effectiveDue, offset)
            val effectiveTrigger =
                ReminderScheduler.computeEffectiveTrigger(rawTrigger, now) ?: return@mapNotNull null
            ProjectedNotification(
                triggerAtMillis = effectiveTrigger,
                title = "${task.title} is coming up",
                body = task.description ?: "Ready when you are.",
                source = ProjectedNotification.Source.TASK_REMINDER,
                sourceId = task.id
            )
        }
    }

    private suspend fun projectHabits(now: Long): List<ProjectedNotification> {
        return habitDao.getHabitsWithDailyTimeReminder().mapNotNull { habit ->
            val reminderTime = habit.reminderTime ?: return@mapNotNull null
            val trigger = HabitReminderScheduler.computeNextDailyTrigger(reminderTime, now)
            ProjectedNotification(
                triggerAtMillis = trigger,
                title = habit.name,
                body = habit.description ?: "${habit.name} — whenever you're ready.",
                source = ProjectedNotification.Source.HABIT_DAILY,
                sourceId = habit.id
            )
        }
    }
}
