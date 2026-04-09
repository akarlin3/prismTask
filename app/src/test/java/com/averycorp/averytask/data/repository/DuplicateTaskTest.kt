package com.averycorp.averytask.data.repository

import com.averycorp.averytask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pure transformation helpers that back [TaskRepository.duplicateTask].
 *
 * The repository method itself wires DAO calls + sync-tracker side effects on top of
 * these helpers, but the interesting logic (what gets copied, what gets reset, how
 * subtask parent pointers are rewired, and how tag cross-refs are rebuilt) is all in
 * the pure functions — so that's where the tests live.
 */
class DuplicateTaskTest {

    private fun sampleTask(
        id: Long = 42L,
        title: String = "Write report",
        parentTaskId: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        description = "Quarterly summary",
        dueDate = 1_700_000_000_000L,
        dueTime = 32_400_000L, // 9am
        priority = 3,
        isCompleted = true,
        projectId = 7L,
        parentTaskId = parentTaskId,
        recurrenceRule = "{\"type\":\"WEEKLY\"}",
        reminderOffset = 15 * 60_000L,
        createdAt = 1_690_000_000_000L,
        updatedAt = 1_695_000_000_000L,
        completedAt = 1_699_000_000_000L,
        archivedAt = 1_699_500_000_000L,
        notes = "Private notes here",
        plannedDate = 1_698_000_000_000L,
        estimatedDuration = 45,
        scheduledStartTime = 1_700_050_000_000L,
        sourceHabitId = 9L,
        sortOrder = 4
    )

    @Test
    fun buildDuplicate_prependsCopyOfPrefixToTitle() {
        val original = sampleTask(title = "Write report")
        val now = 1_800_000_000_000L

        val duplicate = TaskRepository.buildDuplicateEntity(original, nextSortOrder = 10, now = now)

        assertEquals("Copy of Write report", duplicate.title)
    }

    @Test
    fun buildDuplicate_copiesContentFieldsFromOriginal() {
        val original = sampleTask()
        val now = 1_800_000_000_000L

        val duplicate = TaskRepository.buildDuplicateEntity(original, nextSortOrder = 10, now = now)

        // Content-ish fields carry over.
        assertEquals(original.description, duplicate.description)
        assertEquals(original.notes, duplicate.notes)
        assertEquals(original.priority, duplicate.priority)
        assertEquals(original.projectId, duplicate.projectId)
        assertEquals(original.recurrenceRule, duplicate.recurrenceRule)
        assertEquals(original.estimatedDuration, duplicate.estimatedDuration)
        assertEquals(original.parentTaskId, duplicate.parentTaskId)
    }

    @Test
    fun buildDuplicate_resetsSchedulingAndCompletionFields() {
        val original = sampleTask()
        val now = 1_800_000_000_000L

        val duplicate = TaskRepository.buildDuplicateEntity(original, nextSortOrder = 10, now = now)

        // Scheduling, completion, and reminder state must be cleared.
        assertNull("dueDate should reset", duplicate.dueDate)
        assertNull("dueTime should reset", duplicate.dueTime)
        assertNull("plannedDate should reset", duplicate.plannedDate)
        assertNull("reminderOffset should reset", duplicate.reminderOffset)
        assertNull("completedAt should reset", duplicate.completedAt)
        assertNull("archivedAt should reset", duplicate.archivedAt)
        assertNull("scheduledStartTime should reset", duplicate.scheduledStartTime)
        assertFalse("isCompleted should reset", duplicate.isCompleted)
        // The original id is wiped so Room can auto-generate a fresh one.
        assertEquals(0L, duplicate.id)
    }

    @Test
    fun buildDuplicate_usesProvidedSortOrderAndTimestamp() {
        val original = sampleTask()
        val now = 1_800_000_000_000L

        val duplicate = TaskRepository.buildDuplicateEntity(original, nextSortOrder = 99, now = now)

        assertEquals(99, duplicate.sortOrder)
        assertEquals(now, duplicate.createdAt)
        assertEquals(now, duplicate.updatedAt)
    }

    @Test
    fun buildSubtaskDuplicate_pointsAtNewParentAndResetsCompletion() {
        val originalSub = sampleTask(
            id = 55L,
            title = "Collect data",
            parentTaskId = 42L
        )
        val now = 1_800_000_000_000L

        val duplicateSub = TaskRepository.buildSubtaskDuplicate(
            originalSubtask = originalSub,
            newParentId = 1001L,
            sortOrder = 2,
            now = now
        )

        // Subtask titles are copied as-is: no "Copy of " prefix on children.
        assertEquals("Collect data", duplicateSub.title)
        assertEquals(1001L, duplicateSub.parentTaskId)
        assertEquals(0L, duplicateSub.id)
        assertEquals(2, duplicateSub.sortOrder)
        assertEquals(now, duplicateSub.createdAt)
        assertEquals(now, duplicateSub.updatedAt)
        assertFalse("Subtask copy must start incomplete", duplicateSub.isCompleted)
        assertNull(duplicateSub.completedAt)
        assertNull(duplicateSub.dueDate)
        assertNull(duplicateSub.plannedDate)
        assertNull(duplicateSub.reminderOffset)
        // Content-ish fields are preserved on the subtask copy.
        assertEquals(originalSub.description, duplicateSub.description)
        assertEquals(originalSub.priority, duplicateSub.priority)
    }

    @Test
    fun buildTagCrossRefs_emitsOneRefPerTagBoundToNewTaskId() {
        val tagIds = listOf(1L, 5L, 8L)
        val newTaskId = 2025L

        val refs = TaskRepository.buildTagCrossRefs(tagIds = tagIds, newTaskId = newTaskId)

        assertEquals(3, refs.size)
        assertTrue(refs.all { it.taskId == newTaskId })
        assertEquals(listOf(1L, 5L, 8L), refs.map { it.tagId })
    }

    @Test
    fun buildTagCrossRefs_withEmptyListProducesEmptyRefs() {
        val refs = TaskRepository.buildTagCrossRefs(tagIds = emptyList(), newTaskId = 42L)
        assertNotNull(refs)
        assertTrue(refs.isEmpty())
    }
}
