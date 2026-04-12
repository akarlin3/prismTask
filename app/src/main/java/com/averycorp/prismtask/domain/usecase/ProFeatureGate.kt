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

    fun isPremium(): Boolean = userTier.value >= UserTier.PREMIUM

    fun isUltra(): Boolean = userTier.value == UserTier.ULTRA

    fun hasAccess(feature: String): Boolean {
        return when (feature) {
            // Pro features (Pro + Premium + Ultra)
            CLOUD_SYNC, TEMPLATE_SYNC, AI_EISENHOWER, AI_POMODORO,
            ANALYTICS_BASIC, TIME_TRACKING, AI_NLP,
            AI_EVENING_SUMMARY, AI_COACHING, AI_TASK_BREAKDOWN -> isPro()

            // Premium features (Premium + Ultra)
            AI_BRIEFING, AI_WEEKLY_PLAN, AI_TIME_BLOCK,
            AI_CONVERSATIONAL,
            COLLABORATION, INTEGRATIONS, ANALYTICS_FULL,
            ANALYTICS_CORRELATIONS, DRIVE_BACKUP,
            AI_REENGAGEMENT, AI_DAILY_PLANNING,
            AI_WEEKLY_INSIGHTS -> isPremium()

            // Ultra-exclusive features (Ultra only)
            AI_SONNET_NLP, AI_SONNET_EISENHOWER, AI_SONNET_POMODORO,
            AI_SONNET_BRIEFING, AI_SONNET_COACHING, AI_SONNET_WEEKLY,
            AI_SONNET_PLANNER, AI_SONNET_EXTRACT,
            AI_PRIORITY_SUPPORT -> isUltra()

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
            ANALYTICS_BASIC, TIME_TRACKING, AI_NLP,
            AI_EVENING_SUMMARY, AI_COACHING, AI_TASK_BREAKDOWN -> UserTier.PRO

            AI_BRIEFING, AI_WEEKLY_PLAN, AI_TIME_BLOCK,
            AI_CONVERSATIONAL,
            COLLABORATION, INTEGRATIONS, ANALYTICS_FULL,
            ANALYTICS_CORRELATIONS, DRIVE_BACKUP,
            AI_REENGAGEMENT, AI_DAILY_PLANNING,
            AI_WEEKLY_INSIGHTS -> UserTier.PREMIUM

            AI_SONNET_NLP, AI_SONNET_EISENHOWER, AI_SONNET_POMODORO,
            AI_SONNET_BRIEFING, AI_SONNET_COACHING, AI_SONNET_WEEKLY,
            AI_SONNET_PLANNER, AI_SONNET_EXTRACT,
            AI_PRIORITY_SUPPORT -> UserTier.ULTRA

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

        // Pro tier (AI coaching)
        const val AI_EVENING_SUMMARY = "ai_evening_summary"
        const val AI_COACHING = "ai_coaching"                 // Triggers 1, 2, 5 (stuck, perfectionism, celebration)
        const val AI_TASK_BREAKDOWN = "ai_task_breakdown"     // Trigger 6 (unlimited task breakdown)

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

        // Premium tier — AI coaching
        const val AI_DAILY_PLANNING = "ai_daily_planning"     // Trigger 3 (energy-adaptive)
        const val AI_REENGAGEMENT = "ai_reengagement"         // Trigger 4 (welcome back)
        const val AI_WEEKLY_INSIGHTS = "ai_weekly_insights"   // Future: weekly pattern analysis

        // Ultra tier — Claude Sonnet AI upgrades
        const val AI_SONNET_NLP = "ai_sonnet_nlp"                   // Enhanced NLP parsing
        const val AI_SONNET_EISENHOWER = "ai_sonnet_eisenhower"     // Smarter categorization
        const val AI_SONNET_POMODORO = "ai_sonnet_pomodoro"         // Better session planning
        const val AI_SONNET_BRIEFING = "ai_sonnet_briefing"         // Richer daily briefings
        const val AI_SONNET_COACHING = "ai_sonnet_coaching"         // Deeper coaching insights
        const val AI_SONNET_WEEKLY = "ai_sonnet_weekly"             // More nuanced weekly review
        const val AI_SONNET_PLANNER = "ai_sonnet_planner"           // Better time block suggestions
        const val AI_SONNET_EXTRACT = "ai_sonnet_extract"           // Better conversation extraction
        const val AI_PRIORITY_SUPPORT = "ai_priority_support"       // Future: priority support channel
    }
}
