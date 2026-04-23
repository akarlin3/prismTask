package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.TimeBlockRequest
import com.averycorp.prismtask.data.remote.api.TimeBlockResponse
import com.averycorp.prismtask.data.remote.api.TimeBlockStatsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [AiTimeBlockUseCase]. The use case gathers per-task
 * Eisenhower / Pomodoro signals and pre-existing scheduled blocks for
 * the horizon window, then forwards them to `/api/v1/ai/time-block`.
 *
 * Zone is pinned to UTC so scheduled_start_time → local "HH:mm"
 * conversions are deterministic across developer machines.
 */
class AiTimeBlockUseCaseTest {
    private val taskDao: TaskDao = mockk()
    private val api: PrismTaskApi = mockk()
    private val utcClock = AiTimeBlockClock.fixed(ZoneId.of("UTC"))

    private fun newUseCase() = AiTimeBlockUseCase(taskDao, api).also {
        it.clock = utcClock
    }

    private fun stubResponse(): TimeBlockResponse = TimeBlockResponse(
        schedule = emptyList(),
        unscheduledTasks = emptyList(),
        stats = TimeBlockStatsResponse(0, 0, 0, 0, 0),
        proposed = true,
        horizonDays = 1
    )

    @Test
    fun request_carriesHorizonDays_forWeek() = runTest {
        coEvery { taskDao.getTasksInHorizonOnce(any(), any()) } returns emptyList()
        coEvery {
            taskDao.getScheduledTasksInHorizonOnce(any(), any())
        } returns emptyList()
        val requestSlot = slot<TimeBlockRequest>()
        coEvery { api.getTimeBlock(capture(requestSlot)) } returns stubResponse()

        newUseCase()(
            anchorDate = LocalDate.of(2026, 4, 22),
            horizon = TimeBlockHorizon.WEEK,
            dayStart = "09:00",
            dayEnd = "18:00",
            blockSizeMinutes = 30,
            includeBreaks = true,
            breakFrequencyMinutes = 90,
            breakDurationMinutes = 15
        )

        assertEquals(7, requestSlot.captured.horizonDays)
        assertEquals("2026-04-22", requestSlot.captured.date)
    }

    @Test
    fun taskSignals_includeEisenhower_and_estimatedSessions() = runTest {
        // 75 min → ceil(75/25) = 3 Pomodoro sessions.
        val task = TaskEntity(
            id = 10L,
            title = "Refactor reducer",
            estimatedDuration = 75,
            eisenhowerQuadrant = "Q2"
        )
        coEvery { taskDao.getTasksInHorizonOnce(any(), any()) } returns listOf(task)
        coEvery { taskDao.getScheduledTasksInHorizonOnce(any(), any()) } returns emptyList()
        val requestSlot = slot<TimeBlockRequest>()
        coEvery { api.getTimeBlock(capture(requestSlot)) } returns stubResponse()

        newUseCase()(
            anchorDate = LocalDate.of(2026, 4, 22),
            horizon = TimeBlockHorizon.TODAY_PLUS_ONE,
            dayStart = "09:00",
            dayEnd = "18:00",
            blockSizeMinutes = 30,
            includeBreaks = false,
            breakFrequencyMinutes = 90,
            breakDurationMinutes = 15
        )

        val signals = requestSlot.captured.taskSignals
        assertEquals(1, signals.size)
        val s = signals.first()
        assertEquals("10", s.taskId)
        assertEquals("Q2", s.eisenhowerQuadrant)
        assertEquals(3, s.estimatedPomodoroSessions)
        assertEquals(75, s.estimatedDurationMinutes)
        assertEquals("estimated_from_duration", s.pomodoroSource)
    }

    @Test
    fun taskSignals_noDuration_omitSessionCount() = runTest {
        val task = TaskEntity(
            id = 11L,
            title = "Open-ended task",
            estimatedDuration = null,
            eisenhowerQuadrant = null
        )
        coEvery { taskDao.getTasksInHorizonOnce(any(), any()) } returns listOf(task)
        coEvery { taskDao.getScheduledTasksInHorizonOnce(any(), any()) } returns emptyList()
        val requestSlot = slot<TimeBlockRequest>()
        coEvery { api.getTimeBlock(capture(requestSlot)) } returns stubResponse()

        newUseCase()(
            anchorDate = LocalDate.of(2026, 4, 22),
            horizon = TimeBlockHorizon.TODAY,
            dayStart = "09:00",
            dayEnd = "18:00",
            blockSizeMinutes = 30,
            includeBreaks = false,
            breakFrequencyMinutes = 90,
            breakDurationMinutes = 15
        )

        val s = requestSlot.captured.taskSignals.first()
        assertNull(s.estimatedPomodoroSessions)
        assertNull(s.estimatedDurationMinutes)
        assertNull(s.pomodoroSource)
        assertNull(s.eisenhowerQuadrant)
    }

    @Test
    fun existingBlocks_includePreScheduledTasks() = runTest {
        // Already scheduled for 2026-04-22 13:00 UTC, 45 min long.
        val scheduledStartMillis = LocalDate.of(2026, 4, 22)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli() + 13 * 60 * 60 * 1000L
        val existing = TaskEntity(
            id = 20L,
            title = "Standing 1:1",
            scheduledStartTime = scheduledStartMillis,
            estimatedDuration = 45
        )
        coEvery { taskDao.getTasksInHorizonOnce(any(), any()) } returns emptyList()
        coEvery {
            taskDao.getScheduledTasksInHorizonOnce(any(), any())
        } returns listOf(existing)
        val requestSlot = slot<TimeBlockRequest>()
        coEvery { api.getTimeBlock(capture(requestSlot)) } returns stubResponse()

        newUseCase()(
            anchorDate = LocalDate.of(2026, 4, 22),
            horizon = TimeBlockHorizon.TODAY,
            dayStart = "09:00",
            dayEnd = "18:00",
            blockSizeMinutes = 30,
            includeBreaks = false,
            breakFrequencyMinutes = 90,
            breakDurationMinutes = 15
        )

        val blocks = requestSlot.captured.existingBlocks
        assertEquals(1, blocks.size)
        val b = blocks.first()
        assertEquals("2026-04-22", b.date)
        assertEquals("13:00", b.start)
        assertEquals("13:45", b.end)
        assertEquals("Standing 1:1", b.title)
        assertEquals("task", b.source)
        assertEquals("20", b.taskId)
    }

    @Test
    fun daoQueries_useHorizonWindow() = runTest {
        coEvery { taskDao.getTasksInHorizonOnce(any(), any()) } returns emptyList()
        coEvery { taskDao.getScheduledTasksInHorizonOnce(any(), any()) } returns emptyList()
        coEvery { api.getTimeBlock(any()) } returns stubResponse()

        val anchor = LocalDate.of(2026, 4, 22)
        newUseCase()(
            anchorDate = anchor,
            horizon = TimeBlockHorizon.WEEK,
            dayStart = "09:00",
            dayEnd = "18:00",
            blockSizeMinutes = 30,
            includeBreaks = false,
            breakFrequencyMinutes = 90,
            breakDurationMinutes = 15
        )

        val zone = ZoneId.of("UTC")
        val expectedStart = anchor.atStartOfDay(zone).toInstant().toEpochMilli()
        val expectedEnd = anchor.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        coVerify { taskDao.getTasksInHorizonOnce(expectedStart, expectedEnd) }
        coVerify { taskDao.getScheduledTasksInHorizonOnce(expectedStart, expectedEnd) }
    }

    @Test
    fun horizonEnum_fromDays_falls_back_to_week_for_unknown() {
        assertEquals(TimeBlockHorizon.TODAY, TimeBlockHorizon.fromDays(1))
        assertEquals(TimeBlockHorizon.TODAY_PLUS_ONE, TimeBlockHorizon.fromDays(2))
        assertEquals(TimeBlockHorizon.WEEK, TimeBlockHorizon.fromDays(7))
        // Unknown values map to WEEK so the UI never dead-ends on a stale
        // server-side default.
        assertEquals(TimeBlockHorizon.WEEK, TimeBlockHorizon.fromDays(99))
    }
}
