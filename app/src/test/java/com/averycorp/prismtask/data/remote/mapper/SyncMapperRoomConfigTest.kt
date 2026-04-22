package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.BoundaryRuleEntity
import com.averycorp.prismtask.data.local.entity.CustomSoundEntity
import com.averycorp.prismtask.data.local.entity.HabitTemplateEntity
import com.averycorp.prismtask.data.local.entity.NlpShortcutEntity
import com.averycorp.prismtask.data.local.entity.NotificationProfileEntity
import com.averycorp.prismtask.data.local.entity.ProjectTemplateEntity
import com.averycorp.prismtask.data.local.entity.SavedFilterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the v1.4.37 Room-entity sync mappers — one per new
 * collection: notification_profiles, custom_sounds, saved_filters,
 * nlp_shortcuts, habit_templates, project_templates, boundary_rules.
 *
 * The contract for each pair of mappers is:
 *  - `entityTo*Map()` → Map<String, Any?> serializes every column except
 *    auto-generated local identifiers that must not survive the cloud
 *    round-trip (local `id` is folded in under "localId" as a cross-device
 *    debug breadcrumb, but never overrides the target id on decode).
 *  - `mapTo*()` decodes back to the Entity using the caller-supplied local
 *    id + cloud id; all other fields come from the map.
 *
 * These tests mirror the earlier [SyncMapperTest] style — construct an
 * Entity, encode, decode, assert every business-visible field survives.
 */
class SyncMapperRoomConfigTest {

    @Test
    fun notificationProfile_roundTripsAllFields() {
        val source = NotificationProfileEntity(
            id = 42,
            name = "Focus",
            offsetsCsv = "60000,0",
            escalation = true,
            escalationIntervalMinutes = 15,
            isBuiltIn = false,
            urgencyTierKey = "high",
            soundId = "custom_17",
            soundVolumePercent = 85,
            soundFadeInMs = 250,
            soundFadeOutMs = 500,
            silent = false,
            vibrationPresetKey = "triple",
            vibrationIntensityKey = "strong",
            vibrationRepeatCount = 3,
            vibrationContinuous = false,
            customVibrationPatternCsv = "100,200,100",
            displayModeKey = "full_screen",
            lockScreenVisibilityKey = "full",
            accentColorHex = "#FF0000",
            badgeModeKey = "unread_today",
            toastPositionKey = "bottom_left",
            escalationChainJson = """{"steps":[1,2]}""",
            quietHoursJson = """{"start":"22:00","end":"07:00"}""",
            snoozeDurationsCsv = "5,10",
            reAlertIntervalMinutes = 3,
            reAlertMaxAttempts = 5,
            watchSyncModeKey = "silent_only",
            watchHapticPresetKey = "double",
            autoSwitchRulesJson = "[]",
            volumeOverride = true,
            createdAt = 1_000L,
            updatedAt = 2_000L
        )
        val map = SyncMapper.notificationProfileToMap(source)
        val decoded = SyncMapper.mapToNotificationProfile(map, localId = source.id, cloudId = "c1")

        assertEquals("c1", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c1"), decoded)
    }

    @Test
    fun customSound_roundTripsAllFields() {
        val source = CustomSoundEntity(
            id = 7,
            name = "Sunrise",
            originalFilename = "sunrise.mp3",
            uri = "file:///data/sounds/sunrise.mp3",
            format = "mp3",
            sizeBytes = 245_760L,
            durationMs = 18_500L,
            createdAt = 1_000L,
            updatedAt = 3_000L
        )
        val map = SyncMapper.customSoundToMap(source)
        val decoded = SyncMapper.mapToCustomSound(map, localId = source.id, cloudId = "c2")

        assertEquals("c2", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c2"), decoded)
    }

    @Test
    fun savedFilter_roundTripsAllFields() {
        val source = SavedFilterEntity(
            id = 3,
            name = "This Week Work",
            filterJson = """{"lifeCategories":["WORK"],"dueWithin":7}""",
            iconEmoji = "💼",
            sortOrder = 2,
            createdAt = 1_000L,
            updatedAt = 4_000L
        )
        val map = SyncMapper.savedFilterToMap(source)
        val decoded = SyncMapper.mapToSavedFilter(map, localId = source.id, cloudId = "c3")

        assertEquals("c3", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c3"), decoded)
    }

    @Test
    fun nlpShortcut_roundTripsAllFields() {
        val source = NlpShortcutEntity(
            id = 11,
            trigger = "eod",
            expansion = "End-of-day review at 17:00 today",
            sortOrder = 1,
            createdAt = 1_000L,
            updatedAt = 5_000L
        )
        val map = SyncMapper.nlpShortcutToMap(source)
        val decoded = SyncMapper.mapToNlpShortcut(map, localId = source.id, cloudId = "c4")

        assertEquals("c4", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c4"), decoded)
    }

    @Test
    fun habitTemplate_roundTripsAllFields() {
        val source = HabitTemplateEntity(
            id = 19,
            name = "Morning walk",
            description = "20 minutes outside before breakfast",
            iconEmoji = "🚶",
            color = "#8BC34A",
            category = "health",
            frequency = "DAILY",
            targetCount = 1,
            activeDaysCsv = "1,2,3,4,5",
            isBuiltIn = false,
            usageCount = 7,
            lastUsedAt = 1_700L,
            createdAt = 1_000L,
            updatedAt = 6_000L
        )
        val map = SyncMapper.habitTemplateToMap(source)
        val decoded = SyncMapper.mapToHabitTemplate(map, localId = source.id, cloudId = "c5")

        assertEquals("c5", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c5"), decoded)
    }

    @Test
    fun projectTemplate_roundTripsAllFields() {
        val source = ProjectTemplateEntity(
            id = 23,
            name = "Sprint kickoff",
            description = "Standard two-week sprint scaffolding",
            color = "#3F51B5",
            iconEmoji = "🚀",
            category = "work",
            taskTemplatesJson = """[{"title":"Planning","priority":3}]""",
            isBuiltIn = false,
            usageCount = 4,
            lastUsedAt = 2_500L,
            createdAt = 1_000L,
            updatedAt = 7_000L
        )
        val map = SyncMapper.projectTemplateToMap(source)
        val decoded = SyncMapper.mapToProjectTemplate(map, localId = source.id, cloudId = "c6")

        assertEquals("c6", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c6"), decoded)
    }

    @Test
    fun boundaryRule_roundTripsAllFields() {
        val source = BoundaryRuleEntity(
            id = 31,
            name = "No Slack after 6pm",
            ruleType = "BLOCK",
            category = "WORK",
            startTime = "18:00",
            endTime = "23:59",
            activeDaysCsv = "1,2,3,4,5",
            isEnabled = true,
            isBuiltIn = false,
            createdAt = 1_000L,
            updatedAt = 8_000L
        )
        val map = SyncMapper.boundaryRuleToMap(source)
        val decoded = SyncMapper.mapToBoundaryRule(map, localId = source.id, cloudId = "c7")

        assertEquals("c7", decoded.cloudId)
        assertEquals(source.copy(cloudId = "c7"), decoded)
    }

    @Test
    fun missingOptionalFields_decodeToDefaults() {
        // Simulate an older-schema cloud document missing a handful of fields
        // that were added later. Decoder must fall back to entity defaults
        // rather than throwing.
        val sparseMap = mapOf<String, Any?>(
            "name" to "Default",
            "offsetsCsv" to "0",
            "createdAt" to 100L,
            "updatedAt" to 200L
        )
        val decoded = SyncMapper.mapToNotificationProfile(sparseMap, localId = 0, cloudId = null)

        assertEquals("Default", decoded.name)
        assertEquals("0", decoded.offsetsCsv)
        assertEquals(100L, decoded.createdAt)
        assertEquals(200L, decoded.updatedAt)
        // Defaults for fields absent in the map
        assertEquals("medium", decoded.urgencyTierKey)
        assertEquals("system_default", decoded.soundId)
        assertEquals(70, decoded.soundVolumePercent)
        assertEquals(false, decoded.silent)
        assertEquals(5, decoded.reAlertIntervalMinutes)
        assertNull(decoded.cloudId)
    }
}
