package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.preferences.GoodEnoughEscalation
import com.averycorp.prismtask.data.preferences.NdPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoodEnoughTimerManagerTest {

    private lateinit var manager: GoodEnoughTimerManager

    private val frPrefs = NdPreferences(
        focusReleaseModeEnabled = true,
        goodEnoughTimersEnabled = true,
        defaultGoodEnoughMinutes = 30,
        goodEnoughEscalation = GoodEnoughEscalation.NUDGE
    )

    @Before
    fun setUp() {
        manager = GoodEnoughTimerManager()
    }

    @Test
    fun `getTotalEditingMinutes starts at zero`() {
        manager.startTracking(0)
        assertEquals(0, manager.getTotalEditingMinutes())
    }

    @Test
    fun `getTotalEditingMinutes uses previous cumulative time`() {
        manager.startTracking(15)
        manager.pause()
        assertEquals(15, manager.getTotalEditingMinutes())
    }

    @Test
    fun `pause stops tracking time`() {
        manager.startTracking(0)
        manager.pause()
        val time1 = manager.getTotalEditingMinutes()
        // Time should not change after pause
        manager.pause()
        val time2 = manager.getTotalEditingMinutes()
        assertEquals(time1, time2)
    }

    @Test
    fun `check returns null when FR mode is off`() {
        manager.startTracking(40)
        manager.pause()
        val prefs = frPrefs.copy(focusReleaseModeEnabled = false)
        assertNull(manager.check(prefs, null))
    }

    @Test
    fun `check returns null when good enough timers disabled`() {
        manager.startTracking(40)
        manager.pause()
        val prefs = frPrefs.copy(goodEnoughTimersEnabled = false)
        assertNull(manager.check(prefs, null))
    }

    @Test
    fun `check returns null during grace period`() {
        manager.startTracking(0) // 0 cumulative minutes
        manager.pause()
        // With 0 minutes editing, should be in grace period
        assertNull(manager.check(frPrefs.copy(defaultGoodEnoughMinutes = 1), null))
    }

    @Test
    fun `check returns Nudge when escalation is NUDGE and threshold exceeded`() {
        manager.startTracking(35) // 35 minutes cumulative
        manager.pause()
        val result = manager.check(frPrefs, null)
        assertNotNull(result)
        assertTrue(result is TimerEvent.Nudge)
        assertEquals(35, result!!.editingMinutes)
    }

    @Test
    fun `check returns Dialog when escalation is DIALOG and threshold exceeded`() {
        manager.startTracking(35)
        manager.pause()
        val prefs = frPrefs.copy(goodEnoughEscalation = GoodEnoughEscalation.DIALOG)
        val result = manager.check(prefs, null)
        assertNotNull(result)
        assertTrue(result is TimerEvent.Dialog)
    }

    @Test
    fun `check returns Lock when escalation is LOCK and threshold exceeded`() {
        manager.startTracking(35)
        manager.pause()
        val prefs = frPrefs.copy(goodEnoughEscalation = GoodEnoughEscalation.LOCK)
        val result = manager.check(prefs, null)
        assertNotNull(result)
        assertTrue(result is TimerEvent.Lock)
    }

    @Test
    fun `per-task override takes precedence over global default`() {
        manager.startTracking(15) // 15 minutes
        manager.pause()
        // Global is 30 min, override is 10 min → should fire
        val result = manager.check(frPrefs, taskGoodEnoughMinutesOverride = 10)
        assertNotNull(result)
    }

    @Test
    fun `per-task override of 0 disables timer`() {
        manager.startTracking(100)
        manager.pause()
        val result = manager.check(frPrefs, taskGoodEnoughMinutesOverride = 0)
        assertNull(result)
    }

    @Test
    fun `grantExtension resets nudge and extends threshold`() {
        manager.startTracking(35) // Past 30 min threshold
        manager.pause()
        val firstCheck = manager.check(frPrefs, null)
        assertNotNull(firstCheck)

        manager.grantExtension()
        // After extension, threshold effectively becomes 40 min
        // 35 < 40, so no trigger
        val secondCheck = manager.check(frPrefs, null)
        assertNull(secondCheck)
    }

    @Test
    fun `getProgress returns correct fraction`() {
        manager.startTracking(15)
        manager.pause()
        val progress = manager.getProgress(frPrefs, null)
        assertEquals(0.5f, progress, 0.01f) // 15/30 = 0.5
    }

    @Test
    fun `getProgress capped at 1_5`() {
        manager.startTracking(60)
        manager.pause()
        val progress = manager.getProgress(frPrefs, null)
        assertEquals(1.5f, progress, 0.01f)
    }
}
