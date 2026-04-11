package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.billing.UserTier
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProFeatureGate @Inject constructor(
    private val billingManager: BillingManager
) {
    val userTier: StateFlow<UserTier> = billingManager.userTier

    fun isPro(): Boolean = userTier.value >= UserTier.PRO

    fun isPremium(): Boolean = userTier.value == UserTier.PREMIUM

    fun hasAccess(feature: String): Boolean {
        return when (feature) {
            // Pro features (Pro + Premium)
            CLOUD_SYNC, TEMPLATE_SYNC, AI_EISENHOWER, AI_POMODORO,
            ANALYTICS_BASIC, TIME_TRACKING, AI_NLP -> isPro()

            // Premium features (Premium only)
            AI_BRIEFING, AI_WEEKLY_PLAN, AI_TIME_BLOCK,
            AI_CONVERSATIONAL,
            COLLABORATION, INTEGRATIONS, ANALYTICS_FULL,
            ANALYTICS_CORRELATIONS, DRIVE_BACKUP -> isPremium()

            // Free features
            else -> true
        }
    }

    /**
     * Returns the minimum tier required for a given feature.
     */
    fun requiredTier(feature: String): UserTier {
        return when (feature) {
            CLOUD_SYNC, TEMPLATE_SYNC, AI_EISENHOWER, AI_POMODORO,
            ANALYTICS_BASIC, TIME_TRACKING, AI_NLP -> UserTier.PRO

            AI_BRIEFING, AI_WEEKLY_PLAN, AI_TIME_BLOCK,
            AI_CONVERSATIONAL,
            COLLABORATION, INTEGRATIONS, ANALYTICS_FULL,
            ANALYTICS_CORRELATIONS, DRIVE_BACKUP -> UserTier.PREMIUM

            else -> UserTier.FREE
        }
    }

    companion object {
        // Pro tier
        const val CLOUD_SYNC = "cloud_sync"
        const val TEMPLATE_SYNC = "template_sync"
        const val AI_EISENHOWER = "ai_eisenhower"
        const val AI_POMODORO = "ai_pomodoro"
        const val AI_NLP = "ai_nlp"
        const val ANALYTICS_BASIC = "analytics_basic"
        const val TIME_TRACKING = "time_tracking"

        // Premium tier
        const val AI_BRIEFING = "ai_briefing"
        const val AI_WEEKLY_PLAN = "ai_weekly_plan"
        const val AI_TIME_BLOCK = "ai_time_block"
        const val COLLABORATION = "collaboration"
        const val INTEGRATIONS = "integrations"
        const val ANALYTICS_FULL = "analytics_full"
        const val ANALYTICS_CORRELATIONS = "analytics_correlations"
        const val DRIVE_BACKUP = "drive_backup"
        const val AI_CONVERSATIONAL = "ai_conversational"
    }
}
