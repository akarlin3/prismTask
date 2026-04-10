package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.billing.BillingManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProFeatureGate @Inject constructor(
    private val billingManager: BillingManager
) {
    val isPro: StateFlow<Boolean> = billingManager.isProUser

    fun requirePro(feature: String): Boolean {
        return isPro.value
    }

    companion object {
        const val AI_EISENHOWER = "ai_eisenhower"
        const val AI_POMODORO = "ai_pomodoro"
        const val AI_NLP = "ai_nlp"
        const val AI_BRIEFING = "ai_briefing"
        const val AI_WEEKLY_PLAN = "ai_weekly_plan"
        const val AI_TIME_BLOCK = "ai_time_block"
        const val CLOUD_SYNC = "cloud_sync"
        const val COLLABORATION = "collaboration"
        const val TEMPLATE_SYNC = "template_sync"
        const val DRIVE_BACKUP = "drive_backup"
    }
}
