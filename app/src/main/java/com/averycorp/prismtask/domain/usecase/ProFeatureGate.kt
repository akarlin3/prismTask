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
            TEMPLATE_SYNC, AI_EISENHOWER, AI_POMODORO,
            ANALYTICS_BASIC, TIME_TRACKING, AI_NLP,
            AI_EVENING_SUMMARY, AI_COACHING, AI_TASK_BREAKDOWN -> isPro()

            AI_BRIEFING, AI_WEEKLY_PLAN, AI_TIME_BLOCK,
            AI_CONVERSATIONAL,
            COLLABORATION, INTEGRATIONS, ANALYTICS_FULL,
            ANALYTICS_CORRELATIONS, DRIVE_BACKUP,
            AI_REENGAGEMENT, AI_DAILY_PLANNING,
            AI_WEEKLY_INSIGHTS -> isPremium()

            AI_SONNET_NLP, AI_SONNET_EISENHOWER, AI_SONNET_POMODORO,
            AI_SONNET_BRIEFING, AI_SONNET_COACHING, AI_SONNET_WEEKLY,
            AI_SONNET_PLANNER, AI_SONNET_EXTRACT,
            AI_PRIORITY_SUPPORT -> isUltra()

            CLOUD_SYNC -> true // free tier — cloud sync available to all users
            else -> true
        }
    }

    fun requiredTier(feature: String): UserTier {
        return when (feature) {
            TEMPLATE_SYNC, AI_EISENHOWER, AI_POMODORO,
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

            CLOUD_SYNC -> UserTier.FREE // cloud sync available to all users
            else -> UserTier.FREE
        }
    }

    companion object {
        const val CLOUD_SYNC = "cloud_sync"
        const val TEMPLATE_SYNC = "template_sync"
        const val AI_EISENHOWER = "ai_eisenhower"
        const val AI_POMODORO = "ai_pomodoro"
        const val AI_NLP = "ai_nlp"
        const val ANALYTICS_BASIC = "analytics_basic"
        const val TIME_TRACKING = "time_tracking"
        const val AI_EVENING_SUMMARY = "ai_evening_summary"
        const val AI_COACHING = "ai_coaching"
        const val AI_TASK_BREAKDOWN = "ai_task_breakdown"
        const val AI_BRIEFING = "ai_briefing"
        const val AI_WEEKLY_PLAN = "ai_weekly_plan"
        const val AI_TIME_BLOCK = "ai_time_block"
        const val COLLABORATION = "collaboration"
        const val INTEGRATIONS = "integrations"
        const val ANALYTICS_FULL = "analytics_full"
        const val ANALYTICS_CORRELATIONS = "analytics_correlations"
        const val DRIVE_BACKUP = "drive_backup"
        const val AI_CONVERSATIONAL = "ai_conversational"
        const val AI_DAILY_PLANNING = "ai_daily_planning"
        const val AI_REENGAGEMENT = "ai_reengagement"
        const val AI_WEEKLY_INSIGHTS = "ai_weekly_insights"
        const val AI_SONNET_NLP = "ai_sonnet_nlp"
        const val AI_SONNET_EISENHOWER = "ai_sonnet_eisenhower"
        const val AI_SONNET_POMODORO = "ai_sonnet_pomodoro"
        const val AI_SONNET_BRIEFING = "ai_sonnet_briefing"
        const val AI_SONNET_COACHING = "ai_sonnet_coaching"
        const val AI_SONNET_WEEKLY = "ai_sonnet_weekly"
        const val AI_SONNET_PLANNER = "ai_sonnet_planner"
        const val AI_SONNET_EXTRACT = "ai_sonnet_extract"
        const val AI_PRIORITY_SUPPORT = "ai_priority_support"
    }
}
