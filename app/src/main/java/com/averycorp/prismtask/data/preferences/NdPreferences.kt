package com.averycorp.prismtask.data.preferences

/**
 * Neurodivergent Mode preferences. Two independent mode toggles (ADHD Mode and
 * Calm Mode) that can each be on or off simultaneously.
 *
 * - **ADHD Mode**: enables task decomposition, focus guard, body doubling,
 *   dopamine/reward features.
 * - **Calm Mode**: enables sensory reduction (animations, colors, sounds, haptics,
 *   contrast).
 */
data class NdPreferences(
    // --- Top-level mode toggles ---
    val adhdModeEnabled: Boolean = false,
    val calmModeEnabled: Boolean = false,

    // --- Calm Mode sub-settings (all flip ON when calmModeEnabled first enabled) ---
    val reduceAnimations: Boolean = false,
    val mutedColorPalette: Boolean = false,
    val quietMode: Boolean = false,
    val reduceHaptics: Boolean = false,
    val softContrast: Boolean = false,

    // --- ADHD Mode sub-settings (all flip ON when adhdModeEnabled first enabled) ---
    val taskDecompositionEnabled: Boolean = false,
    val focusGuardEnabled: Boolean = false,
    val bodyDoublingEnabled: Boolean = false,
    val checkInIntervalMinutes: Int = 25,
    val completionAnimations: Boolean = false,
    val streakCelebrations: Boolean = false,
    val showProgressBars: Boolean = false,
    val forgivenessStreaks: Boolean = false
)

/**
 * Returns true when reward animations (task completion, streak milestones) should
 * play. Reward animations always respect their own toggle regardless of
 * [NdPreferences.reduceAnimations] — the two are independent concerns.
 *
 * When both ADHD Mode and Calm Mode are active, idle/transition animations are
 * suppressed (reduceAnimations = true) but completion celebrations still play in a
 * simplified form (e.g., checkmark scale-up with glow instead of confetti).
 */
fun shouldShowRewardAnimation(prefs: NdPreferences): Boolean =
    prefs.completionAnimations
