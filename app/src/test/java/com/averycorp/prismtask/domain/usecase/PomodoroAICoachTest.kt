package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.remote.api.PomodoroCoachingRequest
import com.averycorp.prismtask.data.remote.api.PomodoroCoachingResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.ui.screens.pomodoro.SessionTask
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroAICoachTest {

    private val api: PrismTaskApi = mockk()
    private val coach = PomodoroAICoach(api)

    @Test
    fun suggestPreSession_happyPath_returnsTrimmedMessage() = runTest {
        val slot = slot<PomodoroCoachingRequest>()
        coEvery { api.getPomodoroCoaching(capture(slot)) } returns
            PomodoroCoachingResponse(message = "  Start with the draft first. ")

        val result = coach.suggestPreSession(
            upcomingTasks = listOf(
                SessionTask(taskId = 1L, title = "Write draft", allocatedMinutes = 25)
            ),
            sessionLengthMinutes = 25
        )

        assertTrue(result.isSuccess)
        assertEquals("Start with the draft first.", result.getOrNull())
        // Prompt payload should carry the trigger + task metadata.
        assertEquals("pre_session", slot.captured.trigger)
        assertEquals(25, slot.captured.sessionLengthMinutes)
        assertEquals("Write draft", slot.captured.upcomingTasks?.firstOrNull()?.title)
    }

    @Test
    fun suggestPreSession_blankMessage_returnsFailure() = runTest {
        coEvery { api.getPomodoroCoaching(any()) } returns
            PomodoroCoachingResponse(message = "   ")

        val result = coach.suggestPreSession(emptyList(), 25)

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun suggestPreSession_apiThrows_returnsFailure() = runTest {
        coEvery { api.getPomodoroCoaching(any()) } throws RuntimeException("boom")

        val result = coach.suggestPreSession(emptyList(), 25)

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun suggestBreakActivity_forwardsRecentSuggestions() = runTest {
        val slot = slot<PomodoroCoachingRequest>()
        coEvery { api.getPomodoroCoaching(capture(slot)) } returns
            PomodoroCoachingResponse(message = "Stretch.")

        coach.suggestBreakActivity(
            elapsedMinutes = 50,
            breakType = "short",
            recentSuggestions = listOf("Drink water.", "Eye rest.")
        )

        assertEquals("break_activity", slot.captured.trigger)
        assertEquals(50, slot.captured.elapsedMinutes)
        assertEquals("short", slot.captured.breakType)
        assertEquals(listOf("Drink water.", "Eye rest."), slot.captured.recentSuggestions)
    }

    @Test
    fun suggestBreakActivity_emptyRecents_omitsField() = runTest {
        val slot = slot<PomodoroCoachingRequest>()
        coEvery { api.getPomodoroCoaching(capture(slot)) } returns
            PomodoroCoachingResponse(message = "Stretch.")

        coach.suggestBreakActivity(
            elapsedMinutes = 25,
            breakType = "short",
            recentSuggestions = emptyList()
        )

        // Empty list should become null in the request payload — keeps the
        // prompt tight when there's nothing to exclude yet.
        assertNull(slot.captured.recentSuggestions)
    }

    @Test
    fun recapSession_partitionsCompletedAndStarted() = runTest {
        val slot = slot<PomodoroCoachingRequest>()
        coEvery { api.getPomodoroCoaching(capture(slot)) } returns
            PomodoroCoachingResponse(message = "Nice work on the draft.")

        val completed = listOf(SessionTask(1L, "Draft", 25))
        val started = listOf(SessionTask(2L, "Review", 25))

        val result = coach.recapSession(completed, started, sessionDurationMinutes = 50)

        assertTrue(result.isSuccess)
        assertEquals("session_recap", slot.captured.trigger)
        assertEquals(50, slot.captured.sessionDurationMinutes)
        assertEquals(1, slot.captured.completedTasks?.size)
        assertEquals("Draft", slot.captured.completedTasks?.first()?.title)
        assertEquals(1, slot.captured.startedTasks?.size)
        assertEquals("Review", slot.captured.startedTasks?.first()?.title)
        coVerify(exactly = 1) { api.getPomodoroCoaching(any()) }
    }
}
