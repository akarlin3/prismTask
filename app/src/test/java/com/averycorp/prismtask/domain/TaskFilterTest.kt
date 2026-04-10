package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.model.DateRange
import com.averycorp.prismtask.domain.model.TagFilterMode
import com.averycorp.prismtask.domain.model.TaskFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFilterTest {

    @Test
    fun default_isNotActive() {
        assertFalse(TaskFilter().isActive())
    }

    @Test
    fun default_activeFilterCount_isZero() {
        assertEquals(0, TaskFilter().activeFilterCount())
    }

    @Test
    fun withTags_isActive() {
        assertTrue(TaskFilter(selectedTagIds = listOf(1L)).isActive())
    }

    @Test
    fun withPriorities_isActive() {
        assertTrue(TaskFilter(selectedPriorities = listOf(3)).isActive())
    }

    @Test
    fun withProjects_isActive() {
        assertTrue(TaskFilter(selectedProjectIds = listOf(1L)).isActive())
    }

    @Test
    fun withDateRange_isActive() {
        assertTrue(TaskFilter(dateRange = DateRange(1000L, 2000L)).isActive())
    }

    @Test
    fun withShowCompleted_isActive() {
        assertTrue(TaskFilter(showCompleted = true).isActive())
    }

    @Test
    fun withShowArchived_isActive() {
        assertTrue(TaskFilter(showArchived = true).isActive())
    }

    @Test
    fun withSearchQuery_isActive() {
        assertTrue(TaskFilter(searchQuery = "test").isActive())
    }

    @Test
    fun blankSearchQuery_isNotActive() {
        assertFalse(TaskFilter(searchQuery = "  ").isActive())
    }

    @Test
    fun multipleFilters_countsCorrectly() {
        val filter = TaskFilter(
            selectedTagIds = listOf(1L, 2L),
            selectedPriorities = listOf(3),
            dateRange = DateRange(1000L, 2000L),
            showCompleted = true
        )
        assertEquals(4, filter.activeFilterCount())
    }

    @Test
    fun allFiltersActive_countsAll() {
        val filter = TaskFilter(
            selectedTagIds = listOf(1L),
            selectedPriorities = listOf(1),
            selectedProjectIds = listOf(1L),
            dateRange = DateRange(0, 100),
            showCompleted = true,
            showArchived = true,
            searchQuery = "test"
        )
        assertEquals(7, filter.activeFilterCount())
    }

    @Test
    fun defaultTagFilterMode_isAny() {
        assertEquals(TagFilterMode.ANY, TaskFilter().tagFilterMode)
    }
}
