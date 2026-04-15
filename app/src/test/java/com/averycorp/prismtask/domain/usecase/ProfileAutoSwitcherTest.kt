package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.usecase.ProfileAutoSwitcher.Companion.dayOfWeek
import com.averycorp.prismtask.domain.usecase.ProfileAutoSwitcher.Companion.osFocusMode
import com.averycorp.prismtask.domain.usecase.ProfileAutoSwitcher.Companion.timeAndDay
import com.averycorp.prismtask.domain.usecase.ProfileAutoSwitcher.Companion.timeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ProfileAutoSwitcherTest {

    private val switcher = ProfileAutoSwitcher()

    @Test
    fun `timeOfDay matches only within window`() {
        val rule = timeOfDay("work", profileId = 10L, start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        assertTrue(rule.matches(context(time = LocalTime.of(10, 30))))
        assertFalse(rule.matches(context(time = LocalTime.of(8, 30))))
        assertFalse(rule.matches(context(time = LocalTime.of(17, 0)))) // end exclusive
    }

    @Test
    fun `timeOfDay overnight window wraps midnight`() {
        val rule = timeOfDay("sleep", profileId = 1L, start = LocalTime.of(22, 0), end = LocalTime.of(7, 0))
        assertTrue(rule.matches(context(time = LocalTime.of(23, 30))))
        assertTrue(rule.matches(context(time = LocalTime.of(3, 0))))
        assertFalse(rule.matches(context(time = LocalTime.of(8, 0))))
    }

    @Test
    fun `equal start and end never match`() {
        val rule = timeOfDay("noop", profileId = 1L, start = LocalTime.of(9, 0), end = LocalTime.of(9, 0))
        assertFalse(rule.matches(context(time = LocalTime.of(9, 0))))
    }

    @Test
    fun `dayOfWeek matches only listed days`() {
        val rule = dayOfWeek("weekend", profileId = 2L, days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        assertTrue(rule.matches(context(day = DayOfWeek.SATURDAY)))
        assertFalse(rule.matches(context(day = DayOfWeek.MONDAY)))
    }

    @Test
    fun `pick returns first matching rule`() {
        val work = timeAndDay(
            "work",
            profileId = 1L,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        val weekend = dayOfWeek("weekend", profileId = 2L, days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val ctx = context(time = LocalTime.of(10, 0), day = DayOfWeek.WEDNESDAY)
        val result = switcher.pick(listOf(work, weekend), ctx, fallbackProfileId = 99L)
        assertEquals(1L, result)
    }

    @Test
    fun `pick returns fallback when no rule matches`() {
        val rule = dayOfWeek("weekend", profileId = 2L, days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val result = switcher.pick(
            listOf(rule),
            context(day = DayOfWeek.WEDNESDAY),
            fallbackProfileId = 42L
        )
        assertEquals(42L, result)
    }

    @Test
    fun `osFocusMode rule respects focus flag`() {
        val rule = osFocusMode("focus", profileId = 5L)
        assertTrue(rule.matches(context(inOsFocus = true)))
        assertFalse(rule.matches(context(inOsFocus = false)))
    }

    private fun context(
        time: LocalTime = LocalTime.of(12, 0),
        day: DayOfWeek = DayOfWeek.MONDAY,
        inOsFocus: Boolean = false,
        hasCalendarEvent: Boolean = false,
        location: String? = null
    ) = ProfileAutoSwitcher.Context(
        time = time,
        day = day,
        inOsFocusMode = inOsFocus,
        hasActiveCalendarEvent = hasCalendarEvent,
        currentLocationTag = location
    )
}
