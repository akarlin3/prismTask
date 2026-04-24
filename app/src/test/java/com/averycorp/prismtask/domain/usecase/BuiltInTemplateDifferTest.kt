package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.seed.BuiltInHabitDefinition
import com.averycorp.prismtask.data.seed.BuiltInStepDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInTemplateDifferTest {

    private val differ = BuiltInTemplateDiffer()

    private fun habit(
        templateKey: String = "builtin_school",
        sourceVersion: Int = 1,
        userModified: Boolean = false,
        detached: Boolean = false,
        name: String = "School",
        targetFrequency: Int = 1
    ) = HabitEntity(
        id = 1L,
        name = name,
        targetFrequency = targetFrequency,
        isBuiltIn = true,
        templateKey = templateKey,
        sourceVersion = sourceVersion,
        isUserModified = userModified,
        isDetachedFromTemplate = detached
    )

    private fun step(stepId: String, label: String, sortOrder: Int = 0, sourceVersion: Int = 1) =
        SelfCareStepEntity(
            id = stepId.hashCode().toLong(),
            stepId = stepId,
            routineType = "morning",
            label = label,
            duration = "5 min",
            tier = "survival",
            phase = "Self-Care",
            sortOrder = sortOrder,
            sourceVersion = sourceVersion
        )

    private fun stepDef(stepId: String, label: String, sortOrder: Int = 0) =
        BuiltInStepDefinition(
            stepId = stepId,
            routineType = "morning",
            label = label,
            duration = "5 min",
            tier = "survival",
            phase = "Self-Care",
            sortOrder = sortOrder
        )

    private fun proposed(version: Int, vararg steps: BuiltInStepDefinition) = BuiltInHabitDefinition(
        templateKey = "builtin_school",
        version = version,
        name = "School",
        description = null,
        frequency = "daily",
        targetCount = 1,
        activeDaysCsv = "",
        steps = steps.toList()
    )

    @Test
    fun no_changes_returns_null() {
        val result = differ.diff(
            habit(sourceVersion = 1),
            steps = emptyList(),
            proposed = proposed(version = 1)
        )
        assertNull(result)
    }

    @Test
    fun detached_row_returns_null() {
        val result = differ.diff(
            habit(sourceVersion = 1, detached = true),
            steps = emptyList(),
            proposed = proposed(version = 2)
        )
        assertNull(result)
    }

    @Test
    fun mismatched_template_key_returns_null() {
        val result = differ.diff(
            habit(templateKey = "builtin_leisure"),
            steps = emptyList(),
            proposed = proposed(version = 2)
        )
        assertNull(result)
    }

    @Test
    fun added_step_appears_in_diff() {
        val diff = differ.diff(
            habit(sourceVersion = 1),
            steps = emptyList(),
            proposed = proposed(version = 2, stepDef("a", "Added Step"))
        )
        assertNotNull(diff)
        assertEquals(1, diff!!.addedSteps.size)
        assertEquals("Added Step", diff.addedSteps.first().label)
        assertTrue(diff.removedSteps.isEmpty())
    }

    @Test
    fun removed_step_appears_only_when_known_to_prior_registry() {
        // The user has step_id "old"; the proposed v2 doesn't know it.
        // Since we can't tell whether this was user-added or registry-removed
        // without prior-version knowledge, we treat unknown step_ids as
        // user-added (preserved) and never as removals from the proposed set.
        val diff = differ.diff(
            habit(sourceVersion = 1),
            steps = listOf(step("old", "Old Step")),
            proposed = proposed(version = 2, stepDef("a", "New Step"))
        )
        assertNotNull(diff)
        // "old" is preserved as user content (it's not in the proposed set)
        assertEquals(1, diff!!.preservedUserSteps.size)
        assertEquals("Old Step", diff.preservedUserSteps.first().label)
        // "a" appears as added
        assertEquals(1, diff.addedSteps.size)
        assertTrue(diff.removedSteps.isEmpty())
    }

    @Test
    fun modified_step_records_per_field_flags() {
        val diff = differ.diff(
            habit(sourceVersion = 1),
            steps = listOf(step("a", "Old Label", sortOrder = 0)),
            proposed = proposed(version = 2, stepDef("a", "New Label", sortOrder = 1))
        )
        assertNotNull(diff)
        assertEquals(1, diff!!.modifiedSteps.size)
        val change = diff.modifiedSteps.first()
        assertTrue(change.labelChanged)
        assertTrue(change.sortOrderChanged)
        assertFalse(change.tierChanged)
    }

    @Test
    fun same_step_no_changes_excluded_from_modified() {
        val diff = differ.diff(
            habit(sourceVersion = 1),
            steps = listOf(step("a", "Step A"), step("b", "Step B", sortOrder = 1)),
            proposed = proposed(
                version = 2,
                stepDef("a", "Step A"),
                stepDef("b", "Step B", sortOrder = 1),
                stepDef("c", "New Step", sortOrder = 2)
            )
        )
        assertNotNull(diff)
        assertTrue("no modifications expected", diff!!.modifiedSteps.isEmpty())
        assertEquals(1, diff.addedSteps.size)
    }

    @Test
    fun habit_field_change_carries_userModified_flag() {
        val diff = differ.diff(
            habit(sourceVersion = 1, userModified = true, targetFrequency = 1),
            steps = emptyList(),
            proposed = proposed(version = 2).copy(targetCount = 2)
        )
        assertNotNull(diff)
        assertEquals(1, diff!!.habitFieldChanges.size)
        val change = diff.habitFieldChanges.first()
        assertEquals("targetFrequency", change.fieldName)
        assertTrue("userModified should propagate", change.userModified)
    }

    @Test
    fun source_version_zero_treated_as_v1() {
        // Pre-versioning rows backfilled to source_version=1 by migration,
        // but defensively a 0 should also surface diffs against v2+.
        val diff = differ.diff(
            habit(sourceVersion = 0),
            steps = emptyList(),
            proposed = proposed(version = 2, stepDef("a", "Added"))
        )
        assertNotNull(diff)
        assertEquals(1, diff!!.fromVersion)
        assertEquals(2, diff.toVersion)
    }

    @Test
    fun proposed_version_not_greater_returns_null() {
        val result = differ.diff(
            habit(sourceVersion = 3),
            steps = emptyList(),
            proposed = proposed(version = 2)
        )
        assertNull(result)
    }
}
