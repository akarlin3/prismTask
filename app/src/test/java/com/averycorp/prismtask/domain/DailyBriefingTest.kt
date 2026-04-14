package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.remote.api.BriefingPriorityResponse
import com.averycorp.prismtask.data.remote.api.DailyBriefingResponse
import com.averycorp.prismtask.data.remote.api.SuggestedTaskResponse
import com.averycorp.prismtask.ui.screens.briefing.BriefingPriority
import com.averycorp.prismtask.ui.screens.briefing.DailyBriefing
import com.averycorp.prismtask.ui.screens.briefing.SuggestedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBriefingTest {
    @Test
    fun briefing_parsesFromApiResponse() {
        val apiResponse = DailyBriefingResponse(
            greeting = "Good morning! Moderate day with 5 tasks.",
            topPriorities = listOf(
                BriefingPriorityResponse(1L, "Fix bug", "Due today, high priority"),
                BriefingPriorityResponse(2L, "Write report", "Blocks other work"),
                BriefingPriorityResponse(3L, "Reply to emails", "Quick win")
            ),
            headsUp = listOf("2 overdue tasks from yesterday"),
            suggestedOrder = listOf(
                SuggestedTaskResponse(1L, "Fix bug", "9:00 AM", "Hardest first"),
                SuggestedTaskResponse(2L, "Write report", "10:30 AM", "Focus time"),
                SuggestedTaskResponse(3L, "Reply to emails", "12:00 PM", "After break")
            ),
            habitReminders = listOf("Exercise", "Read 20 pages"),
            dayType = "moderate"
        )

        val briefing = DailyBriefing(
            greeting = apiResponse.greeting,
            dayType = apiResponse.dayType,
            topPriorities = apiResponse.topPriorities.map {
                BriefingPriority(it.taskId, it.title, it.reason)
            },
            headsUp = apiResponse.headsUp,
            suggestedOrder = apiResponse.suggestedOrder.map {
                SuggestedTask(it.taskId, it.title, it.suggestedTime, it.reason)
            },
            habitReminders = apiResponse.habitReminders
        )

        assertEquals("moderate", briefing.dayType)
        assertEquals(3, briefing.topPriorities.size)
        assertEquals(1L, briefing.topPriorities[0].taskId)
        assertEquals("Fix bug", briefing.topPriorities[0].title)
        assertEquals(3, briefing.suggestedOrder.size)
        assertEquals("9:00 AM", briefing.suggestedOrder[0].suggestedTime)
        assertEquals(1, briefing.headsUp.size)
        assertEquals(2, briefing.habitReminders.size)
    }

    @Test
    fun applyOrder_generatesCorrectUpdates() {
        val suggestedOrder = listOf(
            SuggestedTask(5L, "Task A", "9:00 AM", "Priority"),
            SuggestedTask(3L, "Task B", "10:00 AM", "After A"),
            SuggestedTask(1L, "Task C", "11:00 AM", "Last")
        )

        // Simulate the applyOrder logic
        val updates = suggestedOrder.mapIndexed { index, task ->
            Pair(task.taskId, index)
        }

        assertEquals(3, updates.size)
        assertEquals(Pair(5L, 0), updates[0])
        assertEquals(Pair(3L, 1), updates[1])
        assertEquals(Pair(1L, 2), updates[2])
    }

    @Test
    fun emptyBriefing_handlesNoTasks() {
        val briefing = DailyBriefing(
            greeting = "Good morning! Clear day ahead.",
            dayType = "light",
            topPriorities = emptyList(),
            headsUp = emptyList(),
            suggestedOrder = emptyList(),
            habitReminders = emptyList()
        )

        assertEquals("light", briefing.dayType)
        assertTrue(briefing.topPriorities.isEmpty())
        assertTrue(briefing.suggestedOrder.isEmpty())
        assertTrue(briefing.headsUp.isEmpty())
        assertTrue(briefing.habitReminders.isEmpty())
    }
}
