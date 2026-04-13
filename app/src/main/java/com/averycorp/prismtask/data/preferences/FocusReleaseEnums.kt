package com.averycorp.prismtask.data.preferences

/**
 * Escalation level for Good Enough Timers (Focus & Release Mode).
 *
 * - [NUDGE]: snackbar at the bottom of the screen — easy to dismiss.
 * - [DIALOG]: blocking dialog asking the user to "Ship it" or keep working.
 * - [LOCK]: editing UI becomes read-only until the user explicitly overrides.
 */
enum class GoodEnoughEscalation {
    NUDGE,
    DIALOG,
    LOCK
}

/**
 * Celebration intensity for Ship-It Celebrations (Focus & Release Mode).
 *
 * - [LOW]: subtle checkmark animation, brief text flash, no sound/haptic.
 * - [MEDIUM]: confetti burst + checkmark + subtle haptic pulse.
 * - [HIGH]: full-screen overlay with large celebration animation and haptic.
 */
enum class CelebrationIntensity {
    LOW,
    MEDIUM,
    HIGH
}
