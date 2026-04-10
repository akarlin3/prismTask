package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.remote.api.DayPlanResponse
import com.averycorp.prismtask.data.remote.api.PlannedTaskResponse
import com.averycorp.prismtask.data.remote.api.UnscheduledTaskResponse
import com.averycorp.prismtask.data.remote.api.WeeklyPlanResponse
import com.averycorp.prismtask.ui.screens.planner.DayPlan
import com.averycorp.prismtask.ui.screens.planner.PlannedTask
import com.averycorp.prismtask.ui.screens.planner.UnscheduledTask
import com.averycorp.prismtask.ui.screens.planner.WeeklyPlan
import com.averycorp.prismtask.ui.screens.planner.WeeklyPlanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyPlannerTest {

    @Test
    fun plan_parsesFromApiResponse() {
        val response = WeeklyPlanResponse(
            plan = mapOf(
                "Monday" to DayPlanResponse(
                    date = "2026-04-13",
                    tasks = listOf(
                        PlannedTaskResponse(1L, "Write report", "9:00 AM", 60, "Due Tuesday")
                    ),
                    totalHours = 1.0,
                    calendarEvents = listOf("Standup 10:00-10:30"),
                    habits = listOf("Exercise")
                ),
                "Tuesday" to DayPlanResponse(
                    date = "2026-04-14",
                    tasks = listOf(
                        PlannedTaskResponse(2L, "Review PR", "9:00 AM", 30, "Quick task")
                    ),
                    totalHours = 0.5,
                    calendarEvents = emptyList(),
                    habits = emptyList()
                )
            ),
            unscheduled = listOf(
                UnscheduledTaskResponse(5L, "Low priority", "Defer to next week")
            ),
            weekSummary = "Light week with 2 tasks.",
            tips = listOf("Focus on the report Monday")
        )

        val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val days = dayOrder.mapNotNull { dayName ->
            response.plan[dayName]?.let { dayPlan ->
                DayPlan(
                    date = dayPlan.date,
                    dayName = dayName,
                    tasks = dayPlan.tasks.map { PlannedTask(it.taskId, it.title, it.suggestedTime, it.durationMinutes, it.reason) },
                    totalHours = dayPlan.totalHours,
                    calendarEvents = dayPlan.calendarEvents,
                    habits = dayPlan.habits
                )
            }
        }

        val plan = WeeklyPlan(
            days = days,
            unscheduled = response.unscheduled.map { UnscheduledTask(it.taskId, it.title, it.reason) },
            weekSummary = response.weekSummary,
            tips = response.tips
        )

        assertEquals(2, plan.days.size)
        assertEquals("Monday", plan.days[0].dayName)
        assertEquals(1, plan.days[0].tasks.size)
        assertEquals(1L, plan.days[0].tasks[0].taskId)
        assertEquals(1, plan.days[0].calendarEvents.size)
        assertEquals(1, plan.unscheduled.size)
        assertEquals("Light week with 2 tasks.", plan.weekSummary)
    }

    @Test
    fun moveTaskToDay_updatesCorrectly() {
        val monday = DayPlan("2026-04-13", "Monday",
            listOf(PlannedTask(1L, "Task A", "9:00", 60, "Reason")),
            1.0, emptyList(), emptyList())
        val tuesday = DayPlan("2026-04-14", "Tuesday",
            emptyList(), 0.0, emptyList(), emptyList())

        val plan = WeeklyPlan(
            days = listOf(monday, tuesday),
            unscheduled = emptyList(),
            weekSummary = "Test",
            tips = emptyList()
        )

        // Simulate move: remove from Monday, add to Tuesday
        val taskToMove = plan.days[0].tasks.find { it.taskId == 1L }!!
        val newDays = plan.days.toMutableList()
        newDays[0] = newDays[0].copy(tasks = newDays[0].tasks.filter { it.taskId != 1L })
        newDays[1] = newDays[1].copy(tasks = newDays[1].tasks + taskToMove)

        val updated = plan.copy(days = newDays)

        assertTrue(updated.days[0].tasks.isEmpty())
        assertEquals(1, updated.days[1].tasks.size)
        assertEquals(1L, updated.days[1].tasks[0].taskId)
    }

    @Test
    fun applyPlan_generatesCorrectTaskUpdates() {
        val plan = WeeklyPlan(
            days = listOf(
                DayPlan("2026-04-13", "Monday",
                    listOf(
                        PlannedTask(1L, "Task A", "9:00", 60, ""),
                        PlannedTask(2L, "Task B", "10:00", 30, "")
                    ), 1.5, emptyList(), emptyList()),
                DayPlan("2026-04-14", "Tuesday",
                    listOf(PlannedTask(3L, "Task C", "9:00", 45, "")),
                    0.75, emptyList(), emptyList())
            ),
            unscheduled = emptyList(),
            weekSummary = "",
            tips = emptyList()
        )

        // Simulate the batch update logic
        val updates = mutableListOf<Triple<Long, String, Int>>() // taskId, date, sortOrder
        for (day in plan.days) {
            for ((index, task) in day.tasks.withIndex()) {
                updates.add(Triple(task.taskId, day.date, index))
            }
        }

        assertEquals(3, updates.size)
        assertEquals(Triple(1L, "2026-04-13", 0), updates[0])
        assertEquals(Triple(2L, "2026-04-13", 1), updates[1])
        assertEquals(Triple(3L, "2026-04-14", 0), updates[2])
    }

    @Test
    fun defaultConfig_hasCorrectValues() {
        val config = WeeklyPlanConfig()
        assertEquals(listOf("MO", "TU", "WE", "TH", "FR"), config.workDays)
        assertEquals(6, config.focusHoursPerDay)
        assertTrue(config.preferFrontLoading)
    }
}
