package com.averycorp.prismtask.ui.screens.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detail screen has to read both JSON shapes that end up in
 * `weekly_reviews.ai_insights_json`:
 *  - worker-produced [com.averycorp.prismtask.data.remote.api.WeeklyReviewResponse]
 *    (wins / slips / patterns / next_week_focus / narrative)
 *  - on-demand ViewModel's legacy narrative (wins / misses / suggestions)
 *
 * A mis-detection here would render blank cards for half the history, so
 * the parser deserves its own round-trip coverage.
 */
class WeeklyReviewContentTest {
    @Test
    fun parse_response_shape_maps_fields_straight() {
        val json = """
            {
              "week_start": "2026-04-13",
              "week_end": "2026-04-19",
              "wins": ["shipped V6"],
              "slips": ["skipped gym"],
              "patterns": ["focused Wednesdays"],
              "next_week_focus": ["protect mornings"],
              "narrative": "Strong week."
            }
        """.trimIndent()

        val body = WeeklyReviewContent.parseInsights(json)

        assertEquals("Strong week.", body.narrative)
        assertEquals(listOf("shipped V6"), body.wins)
        assertEquals(listOf("skipped gym"), body.slips)
        assertEquals(listOf("focused Wednesdays"), body.patterns)
        assertEquals(listOf("protect mornings"), body.nextWeekFocus)
    }

    @Test
    fun parse_legacy_narrative_shape_maps_misses_to_slips() {
        val json = """
            {
              "wins": ["showed up"],
              "misses": ["planning too optimistic"],
              "suggestions": ["lighter next week"]
            }
        """.trimIndent()

        val body = WeeklyReviewContent.parseInsights(json)

        assertEquals("", body.narrative)
        assertEquals(listOf("showed up"), body.wins)
        assertEquals(listOf("planning too optimistic"), body.slips)
        assertTrue(body.patterns.isEmpty())
        assertEquals(listOf("lighter next week"), body.nextWeekFocus)
    }

    @Test
    fun parse_null_or_blank_returns_empty_body() {
        val empty = WeeklyReviewContent.parseInsights(null)
        assertEquals("", empty.narrative)
        assertTrue(empty.wins.isEmpty())

        val blank = WeeklyReviewContent.parseInsights("   ")
        assertEquals("", blank.narrative)
    }

    @Test
    fun parse_metrics_reads_top_level_counts_and_categories() {
        val json = """
            {
              "weekStart": 1000,
              "weekEnd": 2000,
              "completed": 7,
              "slipped": 2,
              "rescheduled": 1,
              "byCategory": { "WORK": 4, "SELF_CARE": 1 }
            }
        """.trimIndent()

        val summary = WeeklyReviewContent.parseMetrics(json)

        assertEquals(7, summary.completed)
        assertEquals(2, summary.slipped)
        assertEquals(1, summary.rescheduled)
        assertEquals(4, summary.byCategory["WORK"])
        assertEquals(1, summary.byCategory["SELF_CARE"])
    }
}
