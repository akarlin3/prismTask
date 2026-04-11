package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.BoundaryRule
import com.averycorp.prismtask.domain.model.BoundaryRuleType
import com.averycorp.prismtask.domain.model.LifeCategory
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

/**
 * Very small regex-based parser for natural-language boundary phrases
 * like "No work after 7pm on weekdays" or "Block work after 8 PM".
 *
 * Supports:
 *  - Leading "No" or "Block" / "Don't schedule" — all interpreted as BLOCK_CATEGORY.
 *  - The category keyword right after: "work", "self-care", "personal",
 *    "health", or their aliases.
 *  - "after HH[am|pm|HHam|HHpm]" or "after HH:MM" for start time — defaults
 *    endTime to 23:59.
 *  - Optional day clause: "on weekdays", "on weekends", "on Monday", etc.
 *
 * Anything that doesn't match returns `null` and the caller falls back to
 * the manual rule editor.
 */
object BoundaryRuleParser {

    private val categoryMap = mapOf(
        "work" to LifeCategory.WORK,
        "personal" to LifeCategory.PERSONAL,
        "self-care" to LifeCategory.SELF_CARE,
        "selfcare" to LifeCategory.SELF_CARE,
        "self care" to LifeCategory.SELF_CARE,
        "health" to LifeCategory.HEALTH
    )

    private val dayClauseMap = mapOf(
        "weekdays" to BoundaryRule.WEEKDAYS,
        "weekday" to BoundaryRule.WEEKDAYS,
        "weekends" to BoundaryRule.WEEKEND,
        "weekend" to BoundaryRule.WEEKEND,
        "every day" to BoundaryRule.ALL_DAYS,
        "monday" to setOf(DayOfWeek.MONDAY),
        "tuesday" to setOf(DayOfWeek.TUESDAY),
        "wednesday" to setOf(DayOfWeek.WEDNESDAY),
        "thursday" to setOf(DayOfWeek.THURSDAY),
        "friday" to setOf(DayOfWeek.FRIDAY),
        "saturday" to setOf(DayOfWeek.SATURDAY),
        "sunday" to setOf(DayOfWeek.SUNDAY)
    )

    fun parse(input: String): BoundaryRule? {
        val text = input.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return null

        // Must start with a blocking cue.
        val blockCuePrefixes = listOf("no ", "don't schedule ", "dont schedule ", "block ", "stop ")
        val cueMatch = blockCuePrefixes.firstOrNull { text.startsWith(it) } ?: return null
        val body = text.removePrefix(cueMatch).trim()

        // Pull category from the front of the body.
        val (category, rest) = extractCategory(body) ?: return null

        // "after HH[am|pm]" → start time
        val timeRegex = Regex("""after\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
        val match = timeRegex.find(rest) ?: return null
        val hourRaw = match.groupValues[1].toInt()
        val minRaw = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]
        val hour = when {
            meridiem == "pm" && hourRaw < 12 -> hourRaw + 12
            meridiem == "am" && hourRaw == 12 -> 0
            else -> hourRaw
        }.coerceIn(0, 23)
        val startTime = LocalTime.of(hour, minRaw.coerceIn(0, 59))

        // Day clause: search anywhere in the rest string.
        val days = dayClauseMap.entries.firstOrNull { (key, _) ->
            rest.contains(key)
        }?.value ?: BoundaryRule.ALL_DAYS

        val name = "No ${category.name.lowercase().replace('_', '-')} after ${BoundaryRule.formatTime(startTime)}"
            .replaceFirstChar { it.uppercase() }
        return BoundaryRule(
            name = name,
            ruleType = BoundaryRuleType.BLOCK_CATEGORY,
            category = category,
            startTime = startTime,
            endTime = LocalTime.of(23, 59),
            activeDays = days
        )
    }

    private fun extractCategory(body: String): Pair<LifeCategory, String>? {
        // Sort keys by descending length so "self care" beats "self".
        val sortedKeys = categoryMap.keys.sortedByDescending { it.length }
        for (key in sortedKeys) {
            if (body.startsWith(key)) {
                return categoryMap[key]!! to body.removePrefix(key).trim()
            }
        }
        return null
    }
}
