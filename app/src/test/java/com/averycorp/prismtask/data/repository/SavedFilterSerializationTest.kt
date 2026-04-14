package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.domain.model.DateRange
import com.averycorp.prismtask.domain.model.TagFilterMode
import com.averycorp.prismtask.domain.model.TaskFilter
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the JSON round-trip used by SavedFilterRepository to persist
 * TaskFilter presets. Using a plain Gson instance mirrors the repository's
 * serialization approach exactly, so any field renames will show up here.
 */
class SavedFilterSerializationTest {
    private val gson = Gson()

    private fun roundTrip(filter: TaskFilter): TaskFilter =
        gson.fromJson(gson.toJson(filter), TaskFilter::class.java)

    @Test
    fun `default filter round trips unchanged`() {
        assertEquals(TaskFilter(), roundTrip(TaskFilter()))
    }

    @Test
    fun `priority only filter round trip preserves selectedPriorities`() {
        val filter = TaskFilter(selectedPriorities = listOf(3, 4))
        val result = roundTrip(filter)
        assertEquals(listOf(3, 4), result.selectedPriorities)
    }

    @Test
    fun `tags and mode round trip`() {
        val filter = TaskFilter(
            selectedTagIds = listOf(1L, 2L, 3L),
            tagFilterMode = TagFilterMode.ALL
        )
        val result = roundTrip(filter)
        assertEquals(listOf(1L, 2L, 3L), result.selectedTagIds)
        assertEquals(TagFilterMode.ALL, result.tagFilterMode)
    }

    @Test
    fun `date range round trips start and end`() {
        val filter = TaskFilter(dateRange = DateRange(1000L, 2000L))
        val result = roundTrip(filter)
        assertEquals(1000L, result.dateRange?.start)
        assertEquals(2000L, result.dateRange?.end)
    }

    @Test
    fun `flagged only round trips`() {
        val filter = TaskFilter(showFlaggedOnly = true)
        val result = roundTrip(filter)
        assertTrue(result.showFlaggedOnly)
    }

    @Test
    fun `project ids round trip including nulls`() {
        val filter = TaskFilter(selectedProjectIds = listOf(1L, null, 3L))
        val result = roundTrip(filter)
        assertEquals(listOf(1L, null, 3L), result.selectedProjectIds)
    }

    @Test
    fun `search query round trips`() {
        val filter = TaskFilter(searchQuery = "buy milk")
        val result = roundTrip(filter)
        assertEquals("buy milk", result.searchQuery)
    }

    @Test
    fun `filter isActive is preserved through round trip`() {
        val filter = TaskFilter(selectedPriorities = listOf(3), showFlaggedOnly = true)
        val result = roundTrip(filter)
        assertTrue(result.isActive())
        assertEquals(2, result.activeFilterCount())
    }
}
