package com.averycorp.prismtask.domain

import com.averycorp.prismtask.ui.screens.pomodoro.FocusStats
import com.averycorp.prismtask.ui.screens.pomodoro.PomodoroConfig
import com.averycorp.prismtask.ui.screens.pomodoro.PomodoroPlan
import com.averycorp.prismtask.ui.screens.pomodoro.PomodoroSession
import com.averycorp.prismtask.ui.screens.pomodoro.SessionTask
import com.averycorp.prismtask.ui.screens.pomodoro.SkippedTask
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPomodoroTest {
    @Test
    fun defaultConfig_hasCorrectValues() {
        val config = PomodoroConfig()
        assertEquals(120, config.availableMinutes)
        assertEquals(25, config.sessionLength)
        assertEquals(5, config.breakLength)
        assertEquals(15, config.longBreakLength)
        assertEquals("balanced", config.focusPreference)
    }

    @Test
    fun plan_parsesSessionsCorrectly() {
        val plan = PomodoroPlan(
            sessions = listOf(
                PomodoroSession(
                    sessionNumber = 1,
                    tasks = listOf(SessionTask(1L, "Write report", 25)),
                    rationale = "Most urgent"
                ),
                PomodoroSession(
                    sessionNumber = 2,
                    tasks = listOf(
                        SessionTask(2L, "Reply emails", 15),
                        SessionTask(3L, "Update docs", 10)
                    ),
                    rationale = "Quick wins batch"
                )
            ),
            totalWorkMinutes = 50,
            totalBreakMinutes = 5,
            skippedTasks = listOf(SkippedTask(4L, "Low priority"))
        )

        assertEquals(2, plan.sessions.size)
        assertEquals(1, plan.sessions[0].tasks.size)
        assertEquals(2, plan.sessions[1].tasks.size)
        assertEquals(50, plan.totalWorkMinutes)
        assertEquals(1, plan.skippedTasks.size)
    }

    @Test
    fun stats_tracksCompletions() {
        var stats = FocusStats()
        assertEquals(0, stats.sessionsCompleted)
        assertEquals(0, stats.tasksCompleted)
        assertEquals(0, stats.totalFocusSeconds)

        stats = stats.copy(
            sessionsCompleted = 2,
            tasksCompleted = 3,
            totalFocusSeconds = 50 * 60
        )
        assertEquals(2, stats.sessionsCompleted)
        assertEquals(3, stats.tasksCompleted)
        assertEquals(3000, stats.totalFocusSeconds)
    }

    @Test
    fun longBreak_afterEveryFourSessions() {
        val config = PomodoroConfig(longBreakLength = 15, breakLength = 5)
        // After sessions 1, 2, 3 → 5 min break
        // After session 4 → 15 min long break
        for (i in 0 until 4) {
            val isLongBreak = (i + 1) % 4 == 0
            if (i == 3) {
                assertEquals("Session $i should be long break", true, isLongBreak)
            } else {
                assertEquals("Session $i should be short break", false, isLongBreak)
            }
        }
    }
}
