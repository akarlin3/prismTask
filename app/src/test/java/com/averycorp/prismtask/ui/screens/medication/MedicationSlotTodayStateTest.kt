package com.averycorp.prismtask.ui.screens.medication

import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MedicationSlotTodayState.isBacklogged] — the derived
 * flag that drives the backlogged-indicator clock icon on the slot's
 * tier chip. Behaviour:
 *
 * - both intended_time and logged_at must be present
 * - difference must exceed the 60s tolerance window
 * - polarity is symmetric (intended < logged or intended > logged)
 *
 * The 60s tolerance avoids flicker for the trivial gap between user
 * tap and DB row landing.
 */
class MedicationSlotTodayStateTest {

    private val slot = MedicationSlotEntity(
        id = 1,
        name = "Morning",
        idealTime = "08:00"
    )

    private fun stateWith(intended: Long?, logged: Long?): MedicationSlotTodayState =
        MedicationSlotTodayState(
            slot = slot,
            medications = emptyList(),
            takenMedicationIds = emptySet(),
            achievedTier = AchievedTier.SKIPPED,
            isUserSet = false,
            intendedTime = intended,
            loggedAt = logged
        )

    @Test
    fun isBacklogged_falseWhenIntendedTimeIsNull() {
        assertFalse(stateWith(intended = null, logged = 1_000L).isBacklogged)
    }

    @Test
    fun isBacklogged_falseWhenLoggedAtIsNull() {
        assertFalse(stateWith(intended = 1_000L, logged = null).isBacklogged)
    }

    @Test
    fun isBacklogged_falseWhenDifferenceUnderToleranceWindow() {
        // 30 seconds gap — within the 60s tolerance, not "backlogged".
        assertFalse(
            stateWith(intended = 1_000_000L, logged = 1_030_000L).isBacklogged
        )
    }

    @Test
    fun isBacklogged_trueWhenLoggedAtIsLaterThanIntendedTimeBeyondTolerance() {
        // 2 minutes after — classic "took at 8 AM, logged at 8:02 AM".
        assertTrue(
            stateWith(intended = 1_000_000L, logged = 1_120_000L).isBacklogged
        )
    }

    @Test
    fun isBacklogged_trueWhenLoggedAtIsBeforeIntendedTimeBeyondTolerance() {
        // Symmetric — shouldn't matter which side the gap is on.
        // (The forward-dating cap in the sheet means this won't normally
        // happen in practice, but the flag logic stays honest.)
        assertTrue(
            stateWith(intended = 1_120_000L, logged = 1_000_000L).isBacklogged
        )
    }

    @Test
    fun isBacklogged_falseWhenIntendedTimeAndLoggedAtAreEqual() {
        assertFalse(
            stateWith(intended = 1_000_000L, logged = 1_000_000L).isBacklogged
        )
    }
}
