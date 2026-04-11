package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SelfCareNudgeEngineTest {

    private val engine = SelfCareNudgeEngine()

    @Test
    fun `no nudge when balanced and burnout low`() {
        val result = engine.select(
            burnoutScore = 20,
            selfCareRatio = 0.20f,
            selfCareTarget = 0.20f,
            hourOfDay = 14,
            lastShownId = null
        )
        assertNull(result)
    }

    @Test
    fun `rest break fires when self-care ratio below target`() {
        val result = engine.select(
            burnoutScore = 10,
            selfCareRatio = 0.05f,
            selfCareTarget = 0.20f,
            hourOfDay = 10,
            lastShownId = null
        )
        assertNotNull(result)
    }

    @Test
    fun `burnout warning fires above threshold`() {
        val result = engine.select(
            burnoutScore = 70,
            selfCareRatio = 0.20f,
            selfCareTarget = 0.20f,
            hourOfDay = 10,
            lastShownId = null
        )
        assertNotNull(result)
    }

    @Test
    fun `wind down only appears after 6pm`() {
        val afternoon = engine.select(
            burnoutScore = 80,
            selfCareRatio = 0.05f,
            selfCareTarget = 0.20f,
            hourOfDay = 14,
            lastShownId = "rest_break"
        )
        val evening = engine.select(
            burnoutScore = 80,
            selfCareRatio = 0.05f,
            selfCareTarget = 0.20f,
            hourOfDay = 19,
            lastShownId = "rest_break"
        )
        // Afternoon: no wind_down option in pool, so we get burnout_warning or movement.
        assertNotNull(afternoon)
        assertEquals(false, afternoon?.id == "wind_down")
        // Evening: wind_down is in the pool.
        assertNotNull(evening)
    }

    @Test
    fun `rotation skips the last shown nudge id`() {
        val first = engine.select(
            burnoutScore = 80,
            selfCareRatio = 0.05f,
            selfCareTarget = 0.20f,
            hourOfDay = 10,
            lastShownId = null
        )
        val second = engine.select(
            burnoutScore = 80,
            selfCareRatio = 0.05f,
            selfCareTarget = 0.20f,
            hourOfDay = 10,
            lastShownId = first?.id
        )
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(false, first?.id == second?.id)
    }
}
