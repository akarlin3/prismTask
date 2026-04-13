package com.averycorp.prismtask.data.preferences

/**
 * Utility object for Neurodivergent Mode feature gating.
 *
 * Determines whether any ND mode is active and which ND features require a Pro
 * subscription. All Calm Mode features, all Focus & Release Mode features, and
 * most ADHD Mode features are Free tier. Only AI-powered features require Pro.
 */
object NdFeatureGate {

    /** Returns true if any ND mode (ADHD, Calm, or Focus & Release) is active. */
    fun isAnyNdActive(prefs: NdPreferences): Boolean =
        prefs.adhdModeEnabled || prefs.calmModeEnabled || prefs.focusReleaseModeEnabled

    /**
     * Returns true if the given ND feature requires a Pro subscription.
     *
     * Pro-gated features:
     * - [AI_DECOMPOSITION]: AI-powered task decomposition via Claude Haiku
     * - [SMART_NUDGES]: Adaptive check-in timing based on productivity patterns
     *
     * Free features: all Calm Mode settings, all Focus & Release Mode settings,
     * manual task decomposition, focus guard, basic completion animations,
     * streak celebrations, progress bars, body doubling with fixed-interval
     * check-ins, forgiveness streaks.
     */
    fun requiresPro(feature: String): Boolean = feature in PRO_FEATURES

    private val PRO_FEATURES = setOf(
        AI_DECOMPOSITION,
        SMART_NUDGES
    )

    // Feature constants
    const val AI_DECOMPOSITION = "ai_decomposition"
    const val SMART_NUDGES = "smart_nudges"
}
