package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial coverage for [MultiCreateDetector] (Phase B / PR-A of the
 * multi-task creation audit). Mirrors the audit doc's Item 2 truth
 * table: the load-bearing trap is `buy milk, eggs, bread` — three
 * comma-separated segments, all task-shaped by length, that the user
 * wants treated as a SINGLE task. The rule clears that case by
 * requiring ≥50% of segments to carry a recognized time marker.
 */
class MultiCreateDetectorTest {

    private val detector = MultiCreateDetector()

    // ---------------------------------------------------------------
    // 6 adversarial false-positives the rule must NOT match
    // ---------------------------------------------------------------

    @Test
    fun singleTask_noPunctuation_stays_NotMulti() {
        val result = detector.detect("pick up groceries 5pm")
        assertEquals(MultiCreateDetector.Result.NotMulti, result)
    }

    @Test
    fun twoSegments_commaInTitle_stays_NotMulti() {
        // 2 segments fails the ≥3 rule even with continuation conjunction.
        val result = detector.detect("email Bob, then call Mary")
        assertEquals(MultiCreateDetector.Result.NotMulti, result)
    }

    @Test
    fun parenthetical_with_continuation_stays_NotMulti() {
        val result = detector.detect("finish report (the long one), or skip if blocked")
        assertEquals(MultiCreateDetector.Result.NotMulti, result)
    }

    @Test
    fun threeNouns_no_timeMarkers_stays_NotMulti() {
        // The load-bearing trap. 3 segments, all task-shaped, but zero
        // time markers — must remain a single task.
        val result = detector.detect("buy milk, eggs, bread")
        assertEquals(MultiCreateDetector.Result.NotMulti, result)
    }

    @Test
    fun fourSegments_below50pct_markers_stays_NotMulti() {
        // 4 segments, only 1 has a marker (25%) — fails the ≥50% rule.
        val result = detector.detect("buy milk, eggs, bread, ice cream tomorrow")
        assertEquals(MultiCreateDetector.Result.NotMulti, result)
    }

    @Test
    fun newlineWithLeadingConjunction_stays_NotMulti() {
        // Two lines but the second starts with a continuation conjunction —
        // the user is writing a continuation, not a second task.
        val result = detector.detect("email Bob today\nor skip if Bob unavailable")
        assertEquals(MultiCreateDetector.Result.NotMulti, result)
    }

    // ---------------------------------------------------------------
    // 6 true-positives the rule MUST match
    // ---------------------------------------------------------------

    @Test
    fun threeCommaSegments_allMarked_isMultiCreate() {
        val result = detector.detect(
            "pick up groceries 5pm, call mom tomorrow, finish report by Friday"
        )
        assertTrue(result is MultiCreateDetector.Result.MultiCreate)
        result as MultiCreateDetector.Result.MultiCreate
        assertEquals(3, result.segments.size)
    }

    @Test
    fun twoLineNewline_isMultiCreate() {
        val result = detector.detect("email Bob today\ncall Mary tomorrow")
        assertTrue(result is MultiCreateDetector.Result.MultiCreate)
        result as MultiCreateDetector.Result.MultiCreate
        assertEquals(2, result.segments.size)
    }

    @Test
    fun threeLineNewline_isMultiCreate() {
        val result = detector.detect(
            "email Bob today\ncall Mary tomorrow\nwrite notes Friday"
        )
        assertTrue(result is MultiCreateDetector.Result.MultiCreate)
        result as MultiCreateDetector.Result.MultiCreate
        assertEquals(3, result.segments.size)
    }

    @Test
    fun mixedCommaAndNewline_newlinesWin_isMultiCreate() {
        // Newline wins over comma. The first line itself contains
        // commas but the detector accepts it as one valid segment.
        val result = detector.detect(
            "email Bob, then call Mary today\nwrite notes Friday"
        )
        assertTrue(result is MultiCreateDetector.Result.MultiCreate)
    }

    @Test
    fun lineWithLeadingWhitespace_isMultiCreate() {
        // Trim is applied per-segment; indented lines still count.
        val result = detector.detect("  email Bob today\n  call Mary tomorrow")
        assertTrue(result is MultiCreateDetector.Result.MultiCreate)
    }

    @Test
    fun threeSegments_clockTimes_isMultiCreate() {
        // "5pm", "9:30", "10am" — all caught by the time-marker regex.
        val result = detector.detect(
            "review PR at 5pm, standup at 9:30, lunch at 10am"
        )
        assertTrue(result is MultiCreateDetector.Result.MultiCreate)
    }

    // ---------------------------------------------------------------
    // Sanity / boundary
    // ---------------------------------------------------------------

    @Test
    fun emptyInput_isNotMulti() {
        assertEquals(MultiCreateDetector.Result.NotMulti, detector.detect(""))
        assertEquals(MultiCreateDetector.Result.NotMulti, detector.detect("   "))
    }
}
