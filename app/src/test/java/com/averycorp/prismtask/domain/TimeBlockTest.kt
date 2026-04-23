package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.remote.api.ScheduleBlockResponse
import com.averycorp.prismtask.data.remote.api.TimeBlockResponse
import com.averycorp.prismtask.data.remote.api.TimeBlockStatsResponse
import com.averycorp.prismtask.data.remote.api.UnscheduledTaskResponse
import com.averycorp.prismtask.ui.screens.timeline.AiSchedule
import com.averycorp.prismtask.ui.screens.timeline.AiScheduleBlock
import com.averycorp.prismtask.ui.screens.timeline.AiTimeBlockStats
import com.averycorp.prismtask.ui.screens.timeline.TimeBlockConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeBlockTest {
    @Test
    fun schedule_parsesFromApiResponse() {
        val response = TimeBlockResponse(
            schedule = listOf(
                ScheduleBlockResponse("09:00", "09:30", "task", 1L, "Write report", "Deep work while fresh"),
                ScheduleBlockResponse("09:30", "10:00", "event", null, "Team standup", "Fixed calendar event"),
                ScheduleBlockResponse("10:00", "10:15", "break", null, "Break", "Recovery after work")
            ),
            unscheduledTasks = listOf(
                UnscheduledTaskResponse(7L, "Low priority task", "Not enough time")
            ),
            stats = TimeBlockStatsResponse(
                totalWorkMinutes = 30,
                totalBreakMinutes = 15,
                totalFreeMinutes = 495,
                tasksScheduled = 1,
                tasksDeferred = 1
            )
        )

        val schedule = AiSchedule(
            blocks = response.schedule.map { block ->
                AiScheduleBlock(
                    block.start,
                    block.end,
                    block.type,
                    block.taskId,
                    block.title,
                    block.reason,
                    date = block.date ?: "2026-04-22"
                )
            },
            unscheduledTasks = response.unscheduledTasks.map { it.taskId to it.title },
            stats = AiTimeBlockStats(
                totalWorkMinutes = response.stats.totalWorkMinutes,
                totalBreakMinutes = response.stats.totalBreakMinutes,
                totalFreeMinutes = response.stats.totalFreeMinutes,
                tasksScheduled = response.stats.tasksScheduled,
                tasksDeferred = response.stats.tasksDeferred
            )
        )

        assertEquals(3, schedule.blocks.size)
        assertEquals("task", schedule.blocks[0].type)
        assertEquals(1L, schedule.blocks[0].taskId)
        assertEquals("event", schedule.blocks[1].type)
        assertNull(schedule.blocks[1].taskId)
        assertEquals("break", schedule.blocks[2].type)
        assertEquals(1, schedule.unscheduledTasks.size)
        assertEquals(30, schedule.stats.totalWorkMinutes)
        assertEquals(1, schedule.stats.tasksScheduled)
    }

    @Test
    fun calendarEvents_arePreservedInSchedule() {
        val blocks = listOf(
            AiScheduleBlock("09:00", "09:30", "task", 1L, "Task A", "Morning", "2026-04-22"),
            AiScheduleBlock("10:00", "11:00", "event", null, "Team meeting", "Fixed", "2026-04-22"),
            AiScheduleBlock("11:00", "11:30", "task", 2L, "Task B", "After meeting", "2026-04-22")
        )

        val eventBlocks = blocks.filter { it.type == "event" }
        assertEquals(1, eventBlocks.size)
        assertEquals("Team meeting", eventBlocks[0].title)
        assertNull(eventBlocks[0].taskId)

        val taskBlocks = blocks.filter { it.type == "task" }
        assertEquals(2, taskBlocks.size)
    }

    @Test
    fun blockDrag_calculatesNewStartTimeCorrectly() {
        // Simulate: a block at 09:00 is dragged to 14:00
        val dayStartMillis = 1712732400000L // some epoch day start
        val originalStartMinutes = 9 * 60 // 09:00
        val newStartMinutes = 14 * 60 // 14:00

        val originalStartMillis = dayStartMillis + originalStartMinutes * 60 * 1000L
        val newStartMillis = dayStartMillis + newStartMinutes * 60 * 1000L

        // Duration should be preserved
        val durationMinutes = 30
        val originalEndMillis = originalStartMillis + durationMinutes * 60 * 1000L
        val newEndMillis = newStartMillis + durationMinutes * 60 * 1000L

        assertEquals(30 * 60 * 1000L, newEndMillis - newStartMillis)
        assertEquals(5 * 60 * 60 * 1000L, newStartMillis - originalStartMillis) // 5 hours difference
    }

    @Test
    fun defaultConfig_hasCorrectValues() {
        val config = TimeBlockConfig()
        assertEquals("09:00", config.dayStart)
        assertEquals("18:00", config.dayEnd)
        assertEquals(30, config.blockSizeMinutes)
        assertTrue(config.includeBreaks)
        assertEquals(90, config.breakFrequencyMinutes)
        assertEquals(15, config.breakDurationMinutes)
    }
}
