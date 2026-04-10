package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.converter.RecurrenceConverter
import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceConverterTest {

    @Test
    fun roundTrip_daily() {
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, interval = 1)
        val json = RecurrenceConverter.toJson(rule)
        val parsed = RecurrenceConverter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(RecurrenceType.DAILY, parsed!!.type)
        assertEquals(1, parsed.interval)
    }

    @Test
    fun roundTrip_weekly_withDays() {
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            interval = 2,
            daysOfWeek = listOf(1, 3, 5)
        )
        val json = RecurrenceConverter.toJson(rule)
        val parsed = RecurrenceConverter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(RecurrenceType.WEEKLY, parsed!!.type)
        assertEquals(2, parsed.interval)
        assertEquals(listOf(1, 3, 5), parsed.daysOfWeek)
    }

    @Test
    fun roundTrip_monthly_withMaxOccurrences() {
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            dayOfMonth = 15,
            maxOccurrences = 12,
            occurrenceCount = 3
        )
        val json = RecurrenceConverter.toJson(rule)
        val parsed = RecurrenceConverter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(RecurrenceType.MONTHLY, parsed!!.type)
        assertEquals(15, parsed.dayOfMonth)
        assertEquals(12, parsed.maxOccurrences)
        assertEquals(3, parsed.occurrenceCount)
    }

    @Test
    fun roundTrip_yearly_withEndDate() {
        val endDate = 1735689600000L // 2025-01-01
        val rule = RecurrenceRule(
            type = RecurrenceType.YEARLY,
            endDate = endDate
        )
        val json = RecurrenceConverter.toJson(rule)
        val parsed = RecurrenceConverter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(RecurrenceType.YEARLY, parsed!!.type)
        assertEquals(endDate, parsed.endDate)
    }

    @Test
    fun roundTrip_skipWeekends() {
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, skipWeekends = true)
        val json = RecurrenceConverter.toJson(rule)
        val parsed = RecurrenceConverter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(true, parsed!!.skipWeekends)
    }

    @Test
    fun fromJson_invalidJson_returnsNull() {
        assertNull(RecurrenceConverter.fromJson("not valid json"))
    }

    @Test
    fun fromJson_emptyObject_returnsDefaults() {
        // Gson will use default values for missing fields
        val parsed = RecurrenceConverter.fromJson("{}")
        assertNotNull(parsed)
    }

    @Test
    fun fromJson_partialJson_handlesGracefully() {
        val json = """{"type":"DAILY"}"""
        val parsed = RecurrenceConverter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(RecurrenceType.DAILY, parsed!!.type)
    }
}
