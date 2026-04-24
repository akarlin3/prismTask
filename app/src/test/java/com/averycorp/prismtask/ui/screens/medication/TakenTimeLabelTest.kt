package com.averycorp.prismtask.ui.screens.medication

import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Unit tests for [takenTimeLabel] — the "Taken at HH:mm" label that
 * renders on every slot card in [MedicationScreen] when the slot has a
 * dose recorded. Covers:
 *
 * - Hides label entirely when no dose has been taken and no user
 *   override exists (otherwise the card would show a meaningless
 *   "Taken at —" line).
 * - Prefers `intended_time` (user-claimed wall-clock) when available.
 * - Falls back to `logged_at` (DB-write moment) when no user override
 *   exists — still more honest than hiding.
 * - When backlogged, surfaces BOTH moments so the gap is legible.
 */
class TakenTimeLabelTest {

    private val defaultLocale = Locale.getDefault()
    private val defaultTz = TimeZone.getDefault()

    private val slot = MedicationSlotEntity(id = 1, name = "Morning", idealTime = "08:00")

    @Before
    fun pinLocale() {
        // The helper uses the device locale for "h:mm a" formatting.
        // Pin to US + UTC so tests don't flake on runners in other locales.
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTz)
    }

    private fun stateWith(
        taken: Set<Long> = emptySet(),
        isUserSet: Boolean = false,
        intended: Long? = null,
        logged: Long? = null
    ) = MedicationSlotTodayState(
        slot = slot,
        medications = emptyList(),
        takenMedicationIds = taken,
        achievedTier = AchievedTier.SKIPPED,
        isUserSet = isUserSet,
        intendedTime = intended,
        loggedAt = logged
    )

    @Test
    fun returnsNullWhenNothingTakenAndNoUserOverride() {
        assertNull(takenTimeLabel(stateWith()))
    }

    @Test
    fun returnsNullWhenTakenButNoTimestampStored() {
        // Defensive: should never happen in practice (the repository
        // writes loggedAt on every upsert), but the helper must not
        // render "Taken at null".
        assertNull(
            takenTimeLabel(stateWith(taken = setOf(1L), intended = null, logged = null))
        )
    }

    @Test
    fun usesLoggedAtWhenNoUserOverride() {
        // 2026-04-24 08:30:00 UTC
        val logged = 1_777_624_200_000L
        val label = takenTimeLabel(stateWith(taken = setOf(1L), logged = logged))
        assertNotNull(label)
        assertEquals("Taken at 8:30 AM", label)
    }

    @Test
    fun prefersIntendedTimeOverLoggedAtWhenTimesMatch() {
        // Not backlogged — gap < 60s — so we show the single "Taken at"
        // line using intended_time (the user's declared truth).
        val intended = 1_777_624_200_000L // 08:30 UTC
        val logged = intended + 30_000L // 30s after intended
        val label = takenTimeLabel(
            stateWith(taken = setOf(1L), intended = intended, logged = logged)
        )
        assertEquals("Taken at 8:30 AM", label)
    }

    @Test
    fun surfacesBothMomentsWhenBacklogged() {
        val intended = 1_777_624_200_000L // 08:30 UTC
        val logged = intended + (2 * 60 * 60 * 1000L) // 10:30 UTC, 2 hr later
        val label = takenTimeLabel(
            stateWith(taken = setOf(1L), intended = intended, logged = logged)
        )
        assertNotNull(label)
        // Exact format: "Taken 8:30 AM · Logged 10:30 AM"
        assertTrue(
            "label should surface both moments, got: $label",
            label!!.contains("Taken 8:30 AM") && label.contains("Logged 10:30 AM")
        )
    }

    @Test
    fun rendersWhenOnlyUserOverrideIsSetEvenWithoutTakenMeds() {
        // Edge case: user long-pressed to stamp a time but hasn't
        // individually checked any medications yet. The "time the user
        // declared they took it" is still meaningful.
        val intended = 1_777_624_200_000L
        val label = takenTimeLabel(
            stateWith(taken = emptySet(), isUserSet = true, intended = intended, logged = intended)
        )
        assertEquals("Taken at 8:30 AM", label)
    }
}
