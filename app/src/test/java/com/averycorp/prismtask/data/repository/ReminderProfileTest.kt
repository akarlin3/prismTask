package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.entity.ReminderProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderProfileTest {

    @Test
    fun `encode and decode offsets round trips`() {
        val offsets = listOf(0L, 900_000L, 86_400_000L)
        val csv = ReminderProfileEntity.encodeOffsets(offsets)
        val decoded = ReminderProfileEntity(
            name = "test",
            offsetsCsv = csv
        ).offsets()
        assertEquals(offsets, decoded)
    }

    @Test
    fun `decode ignores non numeric and empty`() {
        val profile = ReminderProfileEntity(name = "x", offsetsCsv = "0,hello,,300")
        assertEquals(listOf(0L, 300L), profile.offsets())
    }

    @Test
    fun `built in profiles have three entries`() {
        assertEquals(3, ReminderProfileRepository.BUILT_INS.size)
        val names = ReminderProfileRepository.BUILT_INS.map { it.first }
        assertTrue("Gentle" in names)
        assertTrue("Aggressive" in names)
        assertTrue("Minimal" in names)
    }

    @Test
    fun `aggressive has escalation enabled`() {
        val aggr = ReminderProfileRepository.BUILT_INS.first { it.first == "Aggressive" }
        assertTrue(aggr.third)
        assertEquals(15, aggr.fourth)
    }

    @Test
    fun `gentle has no escalation`() {
        val gentle = ReminderProfileRepository.BUILT_INS.first { it.first == "Gentle" }
        assertTrue(!gentle.third)
        assertEquals(null, gentle.fourth)
    }

    @Test
    fun `minimal has a single zero offset`() {
        val min = ReminderProfileRepository.BUILT_INS.first { it.first == "Minimal" }
        assertEquals(listOf(0L), min.second)
    }
}
