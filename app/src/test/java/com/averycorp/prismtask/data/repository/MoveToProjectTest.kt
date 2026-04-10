package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pure transformation helpers that back
 * [TaskRepository.moveToProject].
 *
 * As with [DuplicateTaskTest], the repository method layers DAO calls +
 * sync-tracker side effects on top of these helpers. The interesting
 * semantic — which task ids get updated, what their projectId / updatedAt
 * become, how the cascade flag toggles subtask propagation, and how null
 * projectIds are handled — is all in the pure companion helpers, so the
 * tests live there too.
 */
class MoveToProjectTest {

    private fun sampleTask(
        id: Long = 100L,
        title: String = "Draft spec",
        projectId: Long? = 5L,
        parentTaskId: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        description = null,
        dueDate = null,
        priority = 2,
        projectId = projectId,
        parentTaskId = parentTaskId,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    // ---------------------------------------------------------------------
    // Test 1: The basic move — update the parent task's projectId and bump
    // updatedAt without touching anything else.
    // ---------------------------------------------------------------------
    @Test
    fun applyProjectMove_updatesProjectIdAndTimestamp() {
        val original = sampleTask(id = 100L, projectId = 5L)
        val now = 2_000_000L

        val result = TaskRepository.applyProjectMove(
            task = original,
            subtasks = emptyList(),
            newProjectId = 42L,
            cascadeSubtasks = false,
            now = now
        )

        assertEquals(1, result.size)
        val moved = result.single()
        assertEquals(42L, moved.projectId)
        assertEquals(now, moved.updatedAt)
        // Nothing unrelated should change.
        assertEquals(original.id, moved.id)
        assertEquals(original.title, moved.title)
        assertEquals(original.priority, moved.priority)
        assertEquals(original.createdAt, moved.createdAt)
    }

    // ---------------------------------------------------------------------
    // Test 2: Cascade — when cascadeSubtasks is true, every subtask is moved
    // along with the parent in the returned list. Parent id comes first so
    // the repository can pass the list straight to the DAO in one batch.
    // ---------------------------------------------------------------------
    @Test
    fun applyProjectMove_withCascade_updatesParentAndAllSubtasks() {
        val parent = sampleTask(id = 100L, projectId = 5L)
        val subtasks = listOf(
            sampleTask(id = 101L, projectId = 5L, parentTaskId = 100L),
            sampleTask(id = 102L, projectId = 5L, parentTaskId = 100L),
            // A subtask that somehow landed in a different project still
            // gets yanked into the parent's new project when cascading —
            // that's the whole point of "move subtasks too".
            sampleTask(id = 103L, projectId = 99L, parentTaskId = 100L)
        )
        val now = 3_000_000L

        val result = TaskRepository.applyProjectMove(
            task = parent,
            subtasks = subtasks,
            newProjectId = 42L,
            cascadeSubtasks = true,
            now = now
        )

        assertEquals(4, result.size)
        assertEquals(100L, result[0].id)
        assertEquals(listOf(100L, 101L, 102L, 103L), result.map { it.id })
        assertTrue("All rows share the new projectId", result.all { it.projectId == 42L })
        assertTrue("All rows share the new updatedAt", result.all { it.updatedAt == now })
    }

    // ---------------------------------------------------------------------
    // Test 3: Null projectId — passing null clears the project association.
    // This is how "None (Remove From Project)" gets wired through.
    // ---------------------------------------------------------------------
    @Test
    fun applyProjectMove_withNullProjectId_removesAssociation() {
        val original = sampleTask(id = 100L, projectId = 7L)
        val now = 4_000_000L

        val result = TaskRepository.applyProjectMove(
            task = original,
            subtasks = emptyList(),
            newProjectId = null,
            cascadeSubtasks = false,
            now = now
        )

        assertEquals(1, result.size)
        val moved = result.single()
        assertNull("projectId must be cleared", moved.projectId)
        assertEquals(now, moved.updatedAt)
    }

    // ---------------------------------------------------------------------
    // Test 4: Cascade = false — subtasks are left alone even when provided.
    // Only the parent's projectId changes; subtasks retain their original
    // projectId, which is what "No, Just This" in the confirmation dialog
    // translates to.
    // ---------------------------------------------------------------------
    @Test
    fun applyProjectMove_withoutCascade_leavesSubtasksAlone() {
        val parent = sampleTask(id = 100L, projectId = 5L)
        val subtasks = listOf(
            sampleTask(id = 101L, projectId = 5L, parentTaskId = 100L),
            sampleTask(id = 102L, projectId = 5L, parentTaskId = 100L)
        )
        val now = 5_000_000L

        val result = TaskRepository.applyProjectMove(
            task = parent,
            subtasks = subtasks,
            newProjectId = 42L,
            cascadeSubtasks = false,
            now = now
        )

        // Only the parent comes back — subtasks are not included, so the
        // repository's batch DAO call only touches the parent row.
        assertEquals(1, result.size)
        assertEquals(100L, result.single().id)
        assertEquals(42L, result.single().projectId)
    }

    // ---------------------------------------------------------------------
    // Supporting assertion: buildMoveTargetIds mirrors the structure of
    // applyProjectMove, returning just the id list the repository forwards
    // to the DAO.
    // ---------------------------------------------------------------------
    @Test
    fun buildMoveTargetIds_withCascadeReturnsParentFirstThenSubtasks() {
        val parent = sampleTask(id = 100L)
        val subtasks = listOf(
            sampleTask(id = 201L, parentTaskId = 100L),
            sampleTask(id = 202L, parentTaskId = 100L)
        )

        val cascade = TaskRepository.buildMoveTargetIds(parent, subtasks, cascadeSubtasks = true)
        val noCascade = TaskRepository.buildMoveTargetIds(parent, subtasks, cascadeSubtasks = false)

        assertEquals(listOf(100L, 201L, 202L), cascade)
        assertEquals(listOf(100L), noCascade)
    }
}
