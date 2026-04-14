package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.preferences.CelebrationIntensity
import com.averycorp.prismtask.data.preferences.NdPreferences
import com.averycorp.prismtask.data.preferences.effectiveCelebrationIntensity

/**
 * Ship-It Celebration trigger types — each gets a different celebration flavor.
 */
enum class CelebrationTrigger {
    /** Normal task completion. */
    NORMAL_COMPLETION,

    /** User shipped via a Good Enough Timer. */
    GOOD_ENOUGH_SHIP,

    /** User resisted re-editing (tapped "You're right, leave it"). */
    RESISTED_REWORK,

    /** User locked a task at max revisions. */
    LOCKED_AT_MAX_REVISIONS
}

/**
 * The celebration to show — contains intensity level, trigger type, and a message.
 */
data class ShipItCelebration(
    val trigger: CelebrationTrigger,
    val intensity: CelebrationIntensity,
    val message: String,
    val isStreakMilestone: Boolean = false,
    val streakDays: Int = 0
)

/**
 * Manages Ship-It Celebrations for Focus & Release Mode.
 */
object ShipItCelebrationManager {
    private val normalMessages = listOf(
        "Shipped!",
        "Done is beautiful.",
        "That\u2019s a wrap.",
        "Out the door.",
        "Progress > perfection."
    )

    private val goodEnoughMessages = listOf(
        "Beat the clock!",
        "Good enough IS good enough.",
        "Time\u2019s up \u2014 and so is this task.",
        "Finished, not perfect. Exactly right."
    )

    private val resistedReworkMessages = listOf(
        "Self-control unlocked.",
        "You left it alone. That\u2019s growth.",
        "Resisted the urge. Respect.",
        "It was already done. You knew that."
    )

    private val lockedMessages = listOf(
        "Final version. No take-backs.",
        "Locked and loaded.",
        "The masterpiece is complete.",
        "No more tweaks. It\u2019s perfect because it\u2019s done."
    )

    /**
     * Create a celebration for the given trigger if F&R mode and celebrations
     * are enabled. Returns null if celebrations should not fire.
     */
    fun createCelebration(
        trigger: CelebrationTrigger,
        ndPrefs: NdPreferences,
        releaseStreakDays: Int = 0
    ): ShipItCelebration? {
        if (!ndPrefs.focusReleaseModeEnabled) return null
        if (!ndPrefs.shipItCelebrationsEnabled) return null

        val intensity = effectiveCelebrationIntensity(ndPrefs)
        val messages = when (trigger) {
            CelebrationTrigger.NORMAL_COMPLETION -> normalMessages
            CelebrationTrigger.GOOD_ENOUGH_SHIP -> goodEnoughMessages
            CelebrationTrigger.RESISTED_REWORK -> resistedReworkMessages
            CelebrationTrigger.LOCKED_AT_MAX_REVISIONS -> lockedMessages
        }

        val milestones = setOf(3, 7, 14, 30)
        val isMilestone = releaseStreakDays in milestones

        return ShipItCelebration(
            trigger = trigger,
            intensity = intensity,
            message = messages.random(),
            isStreakMilestone = isMilestone,
            streakDays = releaseStreakDays
        )
    }

    /**
     * Determines which celebration system should fire — Ship-It or ADHD.
     * Ship-It takes priority when F&R mode is active.
     */
    fun shouldFireInsteadOfAdhd(ndPrefs: NdPreferences): Boolean =
        ndPrefs.focusReleaseModeEnabled && ndPrefs.shipItCelebrationsEnabled
}
