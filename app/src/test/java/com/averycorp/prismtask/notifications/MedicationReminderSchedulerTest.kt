package com.averycorp.prismtask.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure companion helpers on [MedicationReminderScheduler] —
 * the per-medication alarm scheduler introduced in the v1.4
 * medication-top-level refactor (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md` §5).
 *
 * Scheduled-time math is covered in
 * [HabitReminderSchedulerDailyTriggerTest] (via the renamed habit
 * scheduler's `computeNextDailyTrigger`). The new scheduler's own
 * [MedicationReminderScheduler.nextTriggerForSpecificTime] uses the same
 * `Calendar.getInstance()` logic, so this file focuses on the bits that
 * are unique to the new scheduler: the request-code namespace and the
 * `"HH:mm"` validator.
 */
class MedicationReminderSchedulerTest {

    @Test
    fun `base request code starts at 400_000`() {
        assertEquals(400_000, MedicationReminderScheduler.baseRequestCode(0))
    }

    @Test
    fun `request codes reserve ten slots per medication`() {
        // medication 1 occupies [400_010, 400_019].
        assertEquals(400_010, MedicationReminderScheduler.baseRequestCode(1))
        assertEquals(400_020, MedicationReminderScheduler.baseRequestCode(2))
        assertEquals(400_030, MedicationReminderScheduler.baseRequestCode(3))
    }

    @Test
    fun `request codes wrap at 1000 medications`() {
        // The % 1000 modulo keeps the namespace bounded. Medication id
        // 1000 collides with id 0 — acceptable because in practice we
        // never expect anywhere near 1000 active medications; the
        // archive-over-delete convention keeps ids stable and reuse rare.
        assertEquals(400_000, MedicationReminderScheduler.baseRequestCode(1000))
        assertEquals(400_010, MedicationReminderScheduler.baseRequestCode(1001))
    }

    @Test
    fun `request codes do not collide with legacy scheduler namespaces`() {
        // Legacy habit scheduler uses +200_000 (interval), +300_000
        // (medication-prefs specific-times), and +900_000 (daily-time).
        // The new medication scheduler's 400_000-range must not overlap.
        val legacyNamespaces = listOf(200_000, 300_000, 900_000)
        for (medId in 0L..10L) {
            val base = MedicationReminderScheduler.baseRequestCode(medId)
            for (legacy in legacyNamespaces) {
                val top = legacy + 10_000 // hypothetical legacy id ceiling
                assertFalse(
                    "medication code $base must not fall in legacy [$legacy, $top)",
                    base in legacy until top
                )
            }
        }
    }

    @Test
    fun `isValidClockString accepts normal times`() {
        assertTrue(MedicationReminderScheduler.isValidClockString("00:00"))
        assertTrue(MedicationReminderScheduler.isValidClockString("08:00"))
        assertTrue(MedicationReminderScheduler.isValidClockString("14:30"))
        assertTrue(MedicationReminderScheduler.isValidClockString("23:59"))
    }

    @Test
    fun `isValidClockString rejects out-of-range hour and minute`() {
        assertFalse(MedicationReminderScheduler.isValidClockString("24:00"))
        assertFalse(MedicationReminderScheduler.isValidClockString("00:60"))
        assertFalse(MedicationReminderScheduler.isValidClockString("-1:00"))
        assertFalse(MedicationReminderScheduler.isValidClockString("99:99"))
    }

    @Test
    fun `isValidClockString rejects malformed strings`() {
        assertFalse(MedicationReminderScheduler.isValidClockString(""))
        assertFalse(MedicationReminderScheduler.isValidClockString("08"))
        assertFalse(MedicationReminderScheduler.isValidClockString("08:00:00"))
        assertFalse(MedicationReminderScheduler.isValidClockString("not:a:time"))
        assertFalse(MedicationReminderScheduler.isValidClockString("ab:cd"))
    }

    @Test
    fun `time-of-day clock mapping uses the canonical bucket times`() {
        val map = MedicationReminderScheduler.TIME_OF_DAY_CLOCK
        assertEquals("08:00", map["morning"])
        assertEquals("13:00", map["afternoon"])
        assertEquals("18:00", map["evening"])
        assertEquals("21:00", map["night"])
        assertEquals(
            "exactly four time-of-day buckets",
            4,
            map.size
        )
    }
}
