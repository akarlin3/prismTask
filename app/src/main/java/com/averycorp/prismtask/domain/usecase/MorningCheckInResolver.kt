package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.repository.HabitWithStatus
import java.time.LocalTime
import java.time.ZoneId

/**
 * A single step in the Morning Check-In guided flow (v1.4.0 V4).
 *
 * Steps are rendered as pages in a HorizontalPager. They carry only
 * the metadata needed to decide whether to show the step — the
 * actual body for each step is a dedicated composable that consumes
 * its data from the relevant repository.
 */
enum class CheckInStep {
    MOOD_ENERGY,   // Optional V7 entry at the top
    MEDICATIONS,
    TOP_TASKS,
    HABITS,
    BALANCE,       // Pro — burnout gauge + category bar
    CALENDAR       // Free — today's events
}

/**
 * Configuration that drives which steps appear. Sourced from the
 * user's preferences and feature flags. Defaults match the vision
 * deck: all steps visible, 11am prompt threshold.
 */
data class MorningCheckInConfig(
    val enabled: Boolean = true,
    val promptBeforeHour: Int = 11,
    val includeMoodEnergy: Boolean = true,
    val includeMedications: Boolean = true,
    val includeTopTasks: Boolean = true,
    val includeHabits: Boolean = true,
    val includeBalance: Boolean = true,
    val includeCalendar: Boolean = true
)

/**
 * Snapshot of whether a check-in should be offered and which steps
 * should appear. The ViewModel layer uses this to decide whether to
 * show the "Start Your Morning Check-In?" prompt on the Today screen.
 */
data class CheckInPlan(
    val shouldPrompt: Boolean,
    val steps: List<CheckInStep>,
    val topTasks: List<TaskEntity>,
    val todayHabits: List<HabitWithStatus>
)

/**
 * Pure-function planner for the morning check-in (v1.4.0 V4).
 *
 * Doesn't touch any state — feed it the raw snapshot of today's tasks,
 * habits, config, last completion date, and "now", and it returns a
 * ready-to-render [CheckInPlan]. Repositories and ViewModels wire
 * this up with their own Flows.
 */
class MorningCheckInResolver {

    fun plan(
        tasks: List<TaskEntity>,
        habits: List<HabitWithStatus>,
        config: MorningCheckInConfig,
        lastCompletedDate: Long?,  // midnight-normalized millis of the last completed check-in
        todayStart: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): CheckInPlan {
        if (!config.enabled) {
            return CheckInPlan(
                shouldPrompt = false,
                steps = emptyList(),
                topTasks = emptyList(),
                todayHabits = emptyList()
            )
        }

        val localNow = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        val beforeThreshold = localNow.isBefore(LocalTime.of(config.promptBeforeHour, 0))
        val alreadyToday = lastCompletedDate != null && lastCompletedDate >= todayStart
        val shouldPrompt = beforeThreshold && !alreadyToday

        // Top 3 tasks due today by highest urgency proxy (priority then due date).
        val topTasks = tasks
            .filter { !it.isCompleted && it.archivedAt == null && it.dueDate != null && it.dueDate < todayStart + DAY }
            .sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
            )
            .take(3)

        val steps = buildList {
            if (config.includeMoodEnergy) add(CheckInStep.MOOD_ENERGY)
            if (config.includeMedications) add(CheckInStep.MEDICATIONS)
            if (config.includeTopTasks) add(CheckInStep.TOP_TASKS)
            if (config.includeHabits && habits.isNotEmpty()) add(CheckInStep.HABITS)
            if (config.includeBalance) add(CheckInStep.BALANCE)
            if (config.includeCalendar) add(CheckInStep.CALENDAR)
        }

        return CheckInPlan(
            shouldPrompt = shouldPrompt,
            steps = steps,
            topTasks = topTasks,
            todayHabits = habits
        )
    }

    companion object {
        private const val DAY: Long = 24L * 60 * 60 * 1000
    }
}
