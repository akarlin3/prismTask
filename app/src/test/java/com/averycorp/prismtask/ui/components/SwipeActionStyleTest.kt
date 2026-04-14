package com.averycorp.prismtask.ui.components

import com.averycorp.prismtask.domain.model.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the swipe action style helper and dispatcher. Verifies that
 * every SwipeAction has a distinct, non-transparent style (except NONE) and
 * that dispatch invokes the correct callback exactly once.
 */
class SwipeActionStyleTest {
    @Test
    fun `every non-none action has a non transparent color`() {
        SwipeAction
            .values()
            .filterNot { it == SwipeAction.NONE }
            .forEach { action ->
                val style = swipeActionStyle(action)
                assertNotNull("Icon for $action should not be null", style.icon)
                assertTrue("Color for $action should have non-zero alpha", style.backgroundColor.alpha > 0f)
                assertTrue("Label for $action should be non-empty", style.label.isNotEmpty())
            }
    }

    @Test
    fun `none action has transparent color and no icon`() {
        val style = swipeActionStyle(SwipeAction.NONE)
        assertEquals(0f, style.backgroundColor.alpha)
        assertNull(style.icon)
        assertEquals("", style.label)
    }

    @Test
    fun `dispatch complete invokes onComplete and consumes swipe`() {
        var completeCalled = 0
        val consumed = dispatchSwipeAction(
            action = SwipeAction.COMPLETE,
            taskId = 7,
            onComplete = {
                assertEquals(7, it)
                completeCalled++
            },
            onDelete = { fail() },
            onReschedule = { fail() },
            onArchive = { fail() },
            onMoveToProject = { fail() },
            onToggleFlag = { fail() }
        )
        assertTrue(consumed)
        assertEquals(1, completeCalled)
    }

    @Test
    fun `dispatch delete invokes onDelete and consumes`() {
        var called = 0
        val consumed = dispatchSwipeAction(
            action = SwipeAction.DELETE,
            taskId = 1,
            onComplete = { fail() },
            onDelete = { called++ },
            onReschedule = { fail() },
            onArchive = { fail() },
            onMoveToProject = { fail() },
            onToggleFlag = { fail() }
        )
        assertTrue(consumed)
        assertEquals(1, called)
    }

    @Test
    fun `dispatch flag toggles without consuming swipe`() {
        var called = 0
        val consumed = dispatchSwipeAction(
            action = SwipeAction.FLAG,
            taskId = 99,
            onComplete = { fail() },
            onDelete = { fail() },
            onReschedule = { fail() },
            onArchive = { fail() },
            onMoveToProject = { fail() },
            onToggleFlag = {
                assertEquals(99, it)
                called++
            }
        )
        assertFalse("FLAG action should not dismiss the row", consumed)
        assertEquals(1, called)
    }

    @Test
    fun `dispatch none does nothing and returns false`() {
        val consumed = dispatchSwipeAction(
            action = SwipeAction.NONE,
            taskId = 1,
            onComplete = { fail() },
            onDelete = { fail() },
            onReschedule = { fail() },
            onArchive = { fail() },
            onMoveToProject = { fail() },
            onToggleFlag = { fail() }
        )
        assertFalse(consumed)
    }

    @Test
    fun `dispatch reschedule invokes reschedule handler`() {
        var called = 0
        dispatchSwipeAction(
            action = SwipeAction.RESCHEDULE,
            taskId = 5,
            onComplete = { fail() },
            onDelete = { fail() },
            onReschedule = { called++ },
            onArchive = { fail() },
            onMoveToProject = { fail() },
            onToggleFlag = { fail() }
        )
        assertEquals(1, called)
    }

    @Test
    fun `dispatch archive invokes archive handler`() {
        var called = 0
        dispatchSwipeAction(
            action = SwipeAction.ARCHIVE,
            taskId = 5,
            onComplete = { fail() },
            onDelete = { fail() },
            onReschedule = { fail() },
            onArchive = { called++ },
            onMoveToProject = { fail() },
            onToggleFlag = { fail() }
        )
        assertEquals(1, called)
    }

    @Test
    fun `dispatch move to project invokes move handler`() {
        var called = 0
        dispatchSwipeAction(
            action = SwipeAction.MOVE_TO_PROJECT,
            taskId = 5,
            onComplete = { fail() },
            onDelete = { fail() },
            onReschedule = { fail() },
            onArchive = { fail() },
            onMoveToProject = { called++ },
            onToggleFlag = { fail() }
        )
        assertEquals(1, called)
    }

    private fun fail(): Nothing = throw AssertionError("Unexpected callback")
}
