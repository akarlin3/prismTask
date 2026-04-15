package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.entity.NotificationProfileEntity
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import com.averycorp.prismtask.domain.model.notifications.VibrationPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replaces the old ReminderProfileTest. Covers the new
 * [NotificationProfileEntity] + [NotificationProfileRepository] contract
 * while retaining the original CSV round-trip and built-in assertions.
 */
class NotificationProfileTest {
    @Test
    fun `encode and decode offsets round trips`() {
        val offsets = listOf(0L, 900_000L, 86_400_000L)
        val csv = NotificationProfileEntity.encodeOffsets(offsets)
        val decoded = NotificationProfileEntity(
            name = "test",
            offsetsCsv = csv
        ).offsets()
        assertEquals(offsets, decoded)
    }

    @Test
    fun `decode ignores non numeric and empty`() {
        val profile = NotificationProfileEntity(name = "x", offsetsCsv = "0,hello,,300")
        assertEquals(listOf(0L, 300L), profile.offsets())
    }

    @Test
    fun `encode and decode snooze durations round trips`() {
        val minutes = listOf(5, 15, 30, 60, 120)
        val encoded = NotificationProfileEntity.encodeSnoozeDurations(minutes)
        val decoded = NotificationProfileEntity(
            name = "test",
            offsetsCsv = "0",
            snoozeDurationsCsv = encoded
        ).snoozeDurations()
        assertEquals(minutes, decoded)
    }

    @Test
    fun `built in profiles include all core templates`() {
        val names = NotificationProfileRepository.BUILT_IN_PROFILES.map { it.name }
        listOf("Default", "Gentle", "Aggressive", "Minimal", "Work", "Focus", "Sleep", "Weekend", "Travel")
            .forEach { assertTrue("missing $it", it in names) }
    }

    @Test
    fun `aggressive profile has escalation and high urgency`() {
        val aggr = NotificationProfileRepository.BUILT_IN_PROFILES.first { it.name == "Aggressive" }
        assertTrue(aggr.escalation)
        assertEquals(15, aggr.escalationIntervalMinutes)
        assertEquals(UrgencyTier.HIGH, aggr.urgencyTier)
    }

    @Test
    fun `minimal profile is silent and non-vibrating`() {
        val min = NotificationProfileRepository.BUILT_IN_PROFILES.first { it.name == "Minimal" }
        assertTrue(min.silent)
        assertEquals(VibrationPreset.NONE, min.vibrationPreset)
        assertEquals(0, min.volumePercent)
    }

    @Test
    fun `template converts to entity with expected defaults`() {
        val template = NotificationProfileRepository.BUILT_IN_PROFILES.first { it.name == "Default" }
        val entity = template.toEntity(now = 1_000L)
        assertEquals("Default", entity.name)
        assertTrue(entity.isBuiltIn)
        assertEquals(1_000L, entity.createdAt)
        assertEquals(UrgencyTier.MEDIUM.key, entity.urgencyTierKey)
        assertEquals(VibrationPreset.SINGLE_PULSE.key, entity.vibrationPresetKey)
    }
}
