package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.BoundaryRule
import com.averycorp.prismtask.domain.model.BoundaryRuleType
import com.averycorp.prismtask.domain.model.LifeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class BoundaryEnforcerTest {
    private val enforcer = BoundaryEnforcer()
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime
            .of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `block rule denies matching category during window`() {
        // Monday April 13, 2026 @ 9pm → inside the evening wind-down.
        val rules = BoundaryEnforcer.BUILT_IN
        val decision = enforcer.evaluate(
            rules = rules,
            category = LifeCategory.WORK,
            now = instant(2026, 4, 13, 21),
            zone = zone
        )
        assertTrue(decision is BoundaryDecision.Block)
    }

    @Test
    fun `block rule does not apply outside window`() {
        // Monday 10am — well outside wind-down.
        val rules = BoundaryEnforcer.BUILT_IN
        val decision = enforcer.evaluate(
            rules = rules,
            category = LifeCategory.WORK,
            now = instant(2026, 4, 13, 10),
            zone = zone
        )
        assertEquals(BoundaryDecision.Allow, decision)
    }

    @Test
    fun `block rule does not apply to other categories`() {
        val rules = BoundaryEnforcer.BUILT_IN
        val decision = enforcer.evaluate(
            rules = rules,
            category = LifeCategory.SELF_CARE,
            now = instant(2026, 4, 13, 21),
            zone = zone
        )
        // Self-care during wind-down → not blocked.
        assertTrue(decision !is BoundaryDecision.Block)
    }

    @Test
    fun `disabled rule is ignored`() {
        val rule = BoundaryRule(
            name = "Disabled",
            ruleType = BoundaryRuleType.BLOCK_CATEGORY,
            category = LifeCategory.WORK,
            startTime = LocalTime.of(0, 0),
            endTime = LocalTime.of(23, 59),
            activeDays = BoundaryRule.ALL_DAYS,
            isEnabled = false
        )
        val decision = enforcer.evaluate(
            rules = listOf(rule),
            category = LifeCategory.WORK,
            now = instant(2026, 4, 13, 21),
            zone = zone
        )
        assertEquals(BoundaryDecision.Allow, decision)
    }

    @Test
    fun `suggest rule surfaces a suggestion instead of blocking`() {
        // Saturday April 11, 2026 → weekend rest rule.
        val rules = BoundaryEnforcer.BUILT_IN
        val decision = enforcer.evaluate(
            rules = rules,
            category = LifeCategory.PERSONAL,
            now = instant(2026, 4, 11, 14),
            zone = zone
        )
        assertTrue(decision is BoundaryDecision.Suggest)
        val suggest = decision as BoundaryDecision.Suggest
        assertEquals(LifeCategory.SELF_CARE, suggest.category)
    }

    @Test
    fun `block takes precedence over suggest`() {
        val block = BoundaryRule(
            name = "Block",
            ruleType = BoundaryRuleType.BLOCK_CATEGORY,
            category = LifeCategory.WORK,
            startTime = LocalTime.of(0, 0),
            endTime = LocalTime.of(23, 59),
            activeDays = BoundaryRule.ALL_DAYS
        )
        val suggest = BoundaryRule(
            name = "Suggest",
            ruleType = BoundaryRuleType.SUGGEST_CATEGORY,
            category = LifeCategory.SELF_CARE,
            startTime = LocalTime.of(0, 0),
            endTime = LocalTime.of(23, 59),
            activeDays = BoundaryRule.ALL_DAYS
        )
        val decision = enforcer.evaluate(
            rules = listOf(suggest, block),
            category = LifeCategory.WORK,
            now = instant(2026, 4, 13, 12),
            zone = zone
        )
        assertTrue(decision is BoundaryDecision.Block)
    }

    @Test
    fun `overnight window straddling midnight still matches early morning`() {
        val nightShift = BoundaryRule(
            name = "Overnight",
            ruleType = BoundaryRuleType.BLOCK_CATEGORY,
            category = LifeCategory.WORK,
            startTime = LocalTime.of(22, 0),
            endTime = LocalTime.of(6, 0),
            activeDays = BoundaryRule.ALL_DAYS
        )
        // 2am is inside the 22:00 → 06:00 window.
        assertTrue(nightShift.containsNow(LocalTime.of(2, 0), DayOfWeek.MONDAY))
        assertTrue(nightShift.containsNow(LocalTime.of(22, 30), DayOfWeek.MONDAY))
        // 10am is outside.
        assertTrue(!nightShift.containsNow(LocalTime.of(10, 0), DayOfWeek.MONDAY))
    }

    @Test
    fun `parser extracts no-work-after-7pm`() {
        val rule = BoundaryRuleParser.parse("No work after 7pm on weekdays")
        assertNotNull(rule)
        assertEquals(LifeCategory.WORK, rule!!.category)
        assertEquals(LocalTime.of(19, 0), rule.startTime)
        assertEquals(BoundaryRule.WEEKDAYS, rule.activeDays)
        assertEquals(BoundaryRuleType.BLOCK_CATEGORY, rule.ruleType)
    }

    @Test
    fun `parser extracts block work after 20`() {
        val rule = BoundaryRuleParser.parse("Block work after 20:00")
        assertNotNull(rule)
        assertEquals(LocalTime.of(20, 0), rule!!.startTime)
    }

    @Test
    fun `parser handles self-care hyphenated`() {
        val rule = BoundaryRuleParser.parse("No self-care after 10pm every day")
        assertNotNull(rule)
        assertEquals(LifeCategory.SELF_CARE, rule!!.category)
        assertEquals(BoundaryRule.ALL_DAYS, rule.activeDays)
    }

    @Test
    fun `parser returns null for unmatched input`() {
        assertEquals(null, BoundaryRuleParser.parse("hello world"))
    }
}
