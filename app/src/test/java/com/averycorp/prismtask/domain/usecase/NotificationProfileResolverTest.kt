package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.NotificationProfileEntity
import com.averycorp.prismtask.domain.model.notifications.EscalationChain
import com.averycorp.prismtask.domain.model.notifications.EscalationStep
import com.averycorp.prismtask.domain.model.notifications.EscalationStepAction
import com.averycorp.prismtask.domain.model.notifications.LockScreenVisibility
import com.averycorp.prismtask.domain.model.notifications.NotificationDisplayMode
import com.averycorp.prismtask.domain.model.notifications.QuietHoursWindow
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import com.averycorp.prismtask.domain.model.notifications.VibrationPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class NotificationProfileResolverTest {
    private val resolver = NotificationProfileResolver.DEFAULT

    @Test
    fun `resolves enum keys back into domain types`() {
        val entity = NotificationProfileEntity(
            id = 1,
            name = "Test",
            offsetsCsv = "900000,0",
            urgencyTierKey = UrgencyTier.HIGH.key,
            vibrationPresetKey = VibrationPreset.TRIPLE.key,
            displayModeKey = NotificationDisplayMode.FULL_SCREEN.key,
            lockScreenVisibilityKey = LockScreenVisibility.HIDDEN.key
        )
        val profile = resolver.resolve(entity)
        assertEquals(UrgencyTier.HIGH, profile.urgencyTier)
        assertEquals(VibrationPreset.TRIPLE, profile.vibrationPreset)
        assertEquals(NotificationDisplayMode.FULL_SCREEN, profile.displayMode)
        assertEquals(LockScreenVisibility.HIDDEN, profile.lockScreenVisibility)
        assertEquals(listOf(900_000L, 0L), profile.reminderOffsetsMs)
    }

    @Test
    fun `coerces out-of-range values`() {
        val entity = NotificationProfileEntity(
            name = "Weird",
            offsetsCsv = "0",
            soundVolumePercent = 150,
            soundFadeInMs = 9999,
            vibrationRepeatCount = 42
        )
        val profile = resolver.resolve(entity)
        assertEquals(100, profile.soundVolumePercent)
        assertEquals(5000, profile.soundFadeInMs)
        assertEquals(10, profile.vibrationRepeatCount)
    }

    @Test
    fun `escalation chain JSON round-trips`() {
        val chain = EscalationChain(
            enabled = true,
            steps = listOf(
                EscalationStep(EscalationStepAction.GENTLE_PING, delayMs = 0L),
                EscalationStep(
                    EscalationStepAction.FULL_SCREEN,
                    delayMs = 300_000L,
                    triggerTiers = setOf(UrgencyTier.CRITICAL)
                )
            ),
            maxAttempts = 2,
            stopOnInteraction = false
        )
        val json = resolver.encodeEscalationChain(chain)
        val entity = NotificationProfileEntity(name = "x", offsetsCsv = "0", escalationChainJson = json)
        val decoded = resolver.resolve(entity).escalation
        assertEquals(chain.enabled, decoded.enabled)
        assertEquals(chain.steps.size, decoded.steps.size)
        assertEquals(EscalationStepAction.FULL_SCREEN, decoded.steps[1].action)
        assertEquals(setOf(UrgencyTier.CRITICAL), decoded.steps[1].triggerTiers)
        assertEquals(2, decoded.maxAttempts)
        assertFalse(decoded.stopOnInteraction)
    }

    @Test
    fun `quiet hours JSON round-trips`() {
        val window = QuietHoursWindow(
            enabled = true,
            start = LocalTime.of(22, 30),
            end = LocalTime.of(7, 15),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            priorityOverrideTiers = setOf(UrgencyTier.HIGH, UrgencyTier.CRITICAL)
        )
        val json = resolver.encodeQuietHours(window)
        val entity = NotificationProfileEntity(name = "x", offsetsCsv = "0", quietHoursJson = json)
        val decoded = resolver.resolve(entity).quietHours
        assertTrue(decoded.enabled)
        assertEquals(LocalTime.of(22, 30), decoded.start)
        assertEquals(LocalTime.of(7, 15), decoded.end)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), decoded.days)
        assertTrue(decoded.canBreakThrough(UrgencyTier.HIGH))
        assertFalse(decoded.canBreakThrough(UrgencyTier.LOW))
    }

    @Test
    fun `legacy escalation flag without JSON backfills into a chain`() {
        val entity = NotificationProfileEntity(
            name = "Legacy",
            offsetsCsv = "0",
            escalation = true,
            escalationIntervalMinutes = 10,
            escalationChainJson = null
        )
        val profile = resolver.resolve(entity)
        assertTrue(profile.escalation.enabled)
        assertTrue(profile.escalation.steps.isNotEmpty())
        assertEquals(10L * 60_000L, profile.escalation.steps.first().delayMs)
    }

    @Test
    fun `malformed JSON falls back to defaults`() {
        val entity = NotificationProfileEntity(
            name = "Bad",
            offsetsCsv = "0",
            escalationChainJson = "{this is not json",
            quietHoursJson = "also not json"
        )
        val profile = resolver.resolve(entity)
        assertFalse(profile.escalation.enabled)
        assertFalse(profile.quietHours.enabled)
    }
}
