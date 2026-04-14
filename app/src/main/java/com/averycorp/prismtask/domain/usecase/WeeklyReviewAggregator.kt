package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.LifeCategory
import java.util.Calendar
import java.util.TimeZone

/**
 * Aggregated stats for a single calendar week (Mon–Sun by default).
 *
 * This is the raw data for both the Weekly Balance Report (V3) and the
 * AI Weekly Review (V6). The two features share the same aggregator so
 * they can never drift.
 *
 * @property weekStart Midnight-normalized start of the week in millis.
 * @property weekEnd Exclusive end of the week in millis.
 * @property completed Count of tasks completed during the window
 *                     (indexed by `completedAt`).
 * @property slipped Count of tasks that were due this week but not
 *                   completed and not archived.
 * @property rescheduled Count of tasks that had their due date pushed
 *                       out past this week's end (approximated via
 *                       `updatedAt > dueDate`; exact tracking would need
 *                       a separate reschedule log).
 * @property byCategory Number of completed tasks per [LifeCategory].
 * @property carryForward Slipped tasks that the "Carry Forward" UI will
 *                        offer for quick reschedule.
 */
data class WeeklyReviewStats(
    val weekStart: Long,
    val weekEnd: Long,
    val completed: Int,
    val slipped: Int,
    val rescheduled: Int,
    val byCategory: Map<LifeCategory, Int>,
    val carryForward: List<TaskEntity>
) {
    val total: Int get() = completed + slipped
    val completionRate: Float
        get() = if (total == 0) 0f else completed.toFloat() / total.toFloat()
}

/**
 * Pure-function weekly stats aggregator for V3 + V6.
 *
 * Independent of Android/Room so it can be unit-tested directly with
 * hand-crafted TaskEntity lists. Callers wire it up with a `Flow` of
 * tasks and a configurable "week start day" (Monday vs Sunday).
 */
class WeeklyReviewAggregator {
    /**
     * Build a [WeeklyReviewStats] snapshot for the week containing [reference].
     * Defaults to ISO-8601 (Monday start). Pass [weekStartDay] = [Calendar.SUNDAY]
     * for the US convention.
     */
    fun aggregate(
        tasks: List<TaskEntity>,
        reference: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        weekStartDay: Int = Calendar.MONDAY
    ): WeeklyReviewStats {
        val weekStart = weekStartMillis(reference, timeZone, weekStartDay)
        val weekEnd = weekStart + SEVEN_DAYS

        var completed = 0
        var slipped = 0
        var rescheduled = 0
        val categoryCounts = LifeCategory.TRACKED.associateWith { 0 }.toMutableMap()
        val carryForward = mutableListOf<TaskEntity>()

        for (task in tasks) {
            val completedAt = task.completedAt
            if (task.isCompleted && completedAt != null && completedAt in weekStart until weekEnd) {
                completed++
                val category = LifeCategory.fromStorage(task.lifeCategory)
                if (category in LifeCategory.TRACKED) {
                    categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
                }
                continue
            }

            val dueDate = task.dueDate
            val wasDueThisWeek = dueDate != null && dueDate in weekStart until weekEnd
            val isStillOpen = !task.isCompleted && task.archivedAt == null
            if (wasDueThisWeek && isStillOpen) {
                slipped++
                carryForward.add(task)
                continue
            }

            // Rescheduled: due date was updated after the task's creation
            // and now falls after this week. Approximate heuristic.
            if (dueDate != null && dueDate >= weekEnd && task.updatedAt > task.createdAt) {
                rescheduled++
            }
        }

        return WeeklyReviewStats(
            weekStart = weekStart,
            weekEnd = weekEnd,
            completed = completed,
            slipped = slipped,
            rescheduled = rescheduled,
            byCategory = categoryCounts,
            carryForward = carryForward.toList()
        )
    }

    /**
     * Normalize a timestamp to the start-of-week midnight.
     */
    internal fun weekStartMillis(
        reference: Long,
        timeZone: TimeZone,
        weekStartDay: Int
    ): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = reference
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.firstDayOfWeek = weekStartDay
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val diff = ((dow - weekStartDay) + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -diff)
        return cal.timeInMillis
    }

    companion object {
        private const val SEVEN_DAYS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
