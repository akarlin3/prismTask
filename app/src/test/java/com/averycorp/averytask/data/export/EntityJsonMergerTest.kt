package com.averycorp.averytask.data.export

import com.averycorp.averytask.data.local.entity.HabitEntity
import com.averycorp.averytask.data.local.entity.ProjectEntity
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the forward/backwards-compatibility guarantees of [mergeEntityWithDefaults],
 * which is the core of the version-3 export/import format:
 *
 *  - A full round trip (entity -> JSON -> entity) preserves every field, so anything
 *    added to an entity is automatically carried by export/import without touching
 *    DataExporter or DataImporter.
 *  - An older export that is missing a field introduced later still loads, filling the
 *    missing field with the entity's Kotlin constructor default.
 *  - A newer export that contains an extra unknown field is tolerated — the extra field
 *    is simply ignored during deserialization.
 *  - Helper fields prefixed with `_` are skipped during merge (they are consumed as
 *    sibling metadata by the importer, e.g. `_projectName`, `_tagNames`).
 */
class EntityJsonMergerTest {

    private val gson = Gson()

    private fun parseObject(json: String): JsonObject =
        JsonParser.parseString(json).asJsonObject

    // ---------------------------------------------------------------------
    // Round-trip: every field on a full entity survives export -> import.
    // ---------------------------------------------------------------------
    @Test
    fun taskEntity_fullRoundTrip_preservesAllFields() {
        val original = TaskEntity(
            id = 42,
            title = "Write tests",
            description = "Cover the merge helper",
            notes = "See CLAUDE.md",
            dueDate = 1_700_000_000_000L,
            dueTime = 1_700_003_600_000L,
            priority = 3,
            isCompleted = false,
            projectId = 7,
            parentTaskId = null,
            recurrenceRule = "{\"type\":\"DAILY\"}",
            reminderOffset = 900_000L,
            createdAt = 1_699_999_000_000L,
            updatedAt = 1_700_000_500_000L,
            completedAt = null,
            archivedAt = null,
            plannedDate = 1_700_000_000_000L,
            estimatedDuration = 30,
            scheduledStartTime = 1_700_002_000_000L,
            sourceHabitId = 99
        )

        val json = gson.toJsonTree(original).asJsonObject
        val merged = mergeEntityWithDefaults(TaskEntity(title = "ignored"), json)

        assertEquals(original, merged)
    }

    // ---------------------------------------------------------------------
    // Backwards compat: an old export missing a field falls back to the
    // Kotlin constructor default on the new entity.
    // ---------------------------------------------------------------------
    @Test
    fun taskEntity_missingNewField_usesConstructorDefault() {
        // Simulates an old export that pre-dates `estimatedDuration` etc.
        val oldJson = parseObject(
            """
            {
              "title": "Old task",
              "description": "from a prior app version",
              "priority": 2,
              "isCompleted": true,
              "createdAt": 1000
            }
            """.trimIndent()
        )

        val merged = mergeEntityWithDefaults(TaskEntity(title = "Old task"), oldJson)

        // Fields present in the JSON come through.
        assertEquals("Old task", merged.title)
        assertEquals("from a prior app version", merged.description)
        assertEquals(2, merged.priority)
        assertTrue(merged.isCompleted)
        assertEquals(1000L, merged.createdAt)

        // Fields absent from the JSON get the entity's constructor defaults.
        assertNull("dueDate should default to null", merged.dueDate)
        assertNull("plannedDate should default to null", merged.plannedDate)
        assertNull("estimatedDuration should default to null", merged.estimatedDuration)
        assertNull("scheduledStartTime should default to null", merged.scheduledStartTime)
        assertNull("sourceHabitId should default to null", merged.sourceHabitId)
        assertNull("notes should default to null", merged.notes)
        assertNull("recurrenceRule should default to null", merged.recurrenceRule)
        assertNull("reminderOffset should default to null", merged.reminderOffset)
    }

    @Test
    fun habitEntity_missingFieldsAddedInLaterVersions_usesDefaults() {
        // Simulates an export from before hasLogging / reminderTimesPerDay existed.
        val oldJson = parseObject(
            """
            {
              "name": "Meditate",
              "targetFrequency": 1,
              "frequencyPeriod": "daily",
              "color": "#FF0000",
              "icon": "🧘",
              "sortOrder": 2,
              "isArchived": false,
              "createdAt": 500,
              "updatedAt": 600
            }
            """.trimIndent()
        )

        val merged = mergeEntityWithDefaults(HabitEntity(name = "Meditate"), oldJson)

        assertEquals("Meditate", merged.name)
        assertEquals("#FF0000", merged.color)
        assertEquals(2, merged.sortOrder)
        assertEquals(500L, merged.createdAt)
        assertEquals(600L, merged.updatedAt)

        // These fields didn't exist in the "old" export — defaults should apply.
        assertEquals(1, merged.reminderTimesPerDay)
        assertFalse(merged.hasLogging)
        assertFalse(merged.createDailyTask)
        assertNull(merged.reminderIntervalMillis)
        assertNull(merged.category)
    }

    // ---------------------------------------------------------------------
    // Forward compat: a newer export with extra unknown fields is tolerated.
    // ---------------------------------------------------------------------
    @Test
    fun taskEntity_extraUnknownField_isIgnored() {
        // Simulates a newer export that has fields the current entity doesn't know about.
        val newJson = parseObject(
            """
            {
              "title": "Future task",
              "priority": 1,
              "futureFieldX": "some value",
              "anotherFuturistic": 12345
            }
            """.trimIndent()
        )

        // Should not throw.
        val merged = mergeEntityWithDefaults(TaskEntity(title = "Future task"), newJson)
        assertEquals("Future task", merged.title)
        assertEquals(1, merged.priority)
    }

    // ---------------------------------------------------------------------
    // v2 backwards compat: the exporter used to write `project` as a string and
    // `tags` as an array directly on the task object. Those are not fields on
    // TaskEntity — they must be ignored by the merge step (the importer reads
    // them separately to resolve foreign keys).
    // ---------------------------------------------------------------------
    @Test
    fun taskEntity_legacyV2ProjectAndTagsFields_areIgnoredByMerge() {
        val v2Json = parseObject(
            """
            {
              "title": "Legacy task",
              "priority": 4,
              "project": "Work",
              "tags": ["urgent", "today"],
              "createdAt": 123
            }
            """.trimIndent()
        )

        val merged = mergeEntityWithDefaults(TaskEntity(title = "Legacy task"), v2Json)

        assertEquals("Legacy task", merged.title)
        assertEquals(4, merged.priority)
        assertEquals(123L, merged.createdAt)
        // The legacy `project` field must not leak into the entity.
        assertNull(merged.projectId)
    }

    // ---------------------------------------------------------------------
    // Helper fields prefixed with `_` must be skipped (they are consumed
    // separately by the importer for foreign-key resolution).
    // ---------------------------------------------------------------------
    @Test
    fun underscoreHelperFields_areSkippedDuringMerge() {
        val json = parseObject(
            """
            {
              "title": "Task with helpers",
              "_projectName": "Work",
              "_tagNames": ["home"],
              "_someOtherHelper": 42
            }
            """.trimIndent()
        )

        val merged = mergeEntityWithDefaults(TaskEntity(title = "Task with helpers"), json)
        assertEquals("Task with helpers", merged.title)
        // Helper fields must not accidentally populate any entity slot.
        assertNull(merged.projectId)
    }

    // ---------------------------------------------------------------------
    // Explicit null in JSON overrides a non-null default.
    // ---------------------------------------------------------------------
    @Test
    fun explicitNullInJson_overridesDefault() {
        val json = parseObject(
            """
            {
              "title": "Cleared task",
              "description": null
            }
            """.trimIndent()
        )

        // The default instance has description = null already, but we want to prove
        // that explicit null wins over any value the default might carry.
        val defaultWithDesc = TaskEntity(title = "Cleared task", description = "should be cleared")
        val merged = mergeEntityWithDefaults(defaultWithDesc, json)

        assertEquals("Cleared task", merged.title)
        assertNull(merged.description)
    }

    // ---------------------------------------------------------------------
    // Projects: simple entity with only name as required field.
    // ---------------------------------------------------------------------
    @Test
    fun projectEntity_roundTripPreservesColorAndIcon() {
        val original = ProjectEntity(
            name = "Home",
            color = "#123456",
            icon = "🏠",
            createdAt = 42L,
            updatedAt = 99L
        )
        val json = gson.toJsonTree(original).asJsonObject
        val merged = mergeEntityWithDefaults(ProjectEntity(name = "ignored"), json)
        assertEquals(original.copy(id = 0), merged.copy(id = 0))
    }

    @Test
    fun projectEntity_emptyJson_keepsConstructorDefaults() {
        val minimalJson = parseObject("""{"name":"Solo"}""")
        val merged = mergeEntityWithDefaults(ProjectEntity(name = "Solo"), minimalJson)
        assertEquals("Solo", merged.name)
        assertEquals("#4A90D9", merged.color)
        // createdAt should have the fresh default value from the constructor, not 0.
        assertTrue("createdAt must come from constructor default", merged.createdAt > 0)
        assertNotNull(merged.icon)
    }
}
