package com.averycorp.prismtask.domain.model.notifications

/**
 * Data classes describing a user-configured escalation chain, which runs
 * a sequence of increasingly-intrusive notifications when a reminder is
 * left unattended.
 *
 * The chain is stored as JSON on [com.averycorp.prismtask.data.local.entity.NotificationProfileEntity]
 * (via a TypeConverter) so profiles remain a single row without a
 * join-heavy N-step schema.
 */
data class EscalationStep(
    val action: EscalationStepAction,
    /** Delay after the previous step (or the initial fire time for step 0) in millis. */
    val delayMs: Long,
    /** Urgency tiers that can trigger this step. Empty = all tiers. */
    val triggerTiers: Set<UrgencyTier> = emptySet()
) {
    fun appliesTo(tier: UrgencyTier): Boolean =
        triggerTiers.isEmpty() || tier in triggerTiers
}

data class EscalationChain(
    val enabled: Boolean = false,
    val steps: List<EscalationStep> = emptyList(),
    /** If true, any user interaction (tap, snooze, dismiss) cancels remaining steps. */
    val stopOnInteraction: Boolean = true,
    /** Max total attempts across all steps. 0 = unbounded (until dismissed). */
    val maxAttempts: Int = 5
) {
    /**
     * Linearizes the chain into absolute millis offsets relative to the
     * initial fire time, filtering by the tier being escalated. Returns an
     * empty list when [enabled] is false or no step applies.
     */
    fun absoluteOffsets(tier: UrgencyTier): List<Long> {
        if (!enabled) return emptyList()
        val accum = mutableListOf<Long>()
        var running = 0L
        for (step in steps) {
            running += step.delayMs
            if (step.appliesTo(tier)) accum += running
        }
        val cap = if (maxAttempts <= 0) accum.size else maxAttempts.coerceAtMost(accum.size)
        return accum.take(cap)
    }

    companion object {
        val DISABLED = EscalationChain(enabled = false, steps = emptyList())

        /** Built-in "gentle → standard → loud → full-screen" chain used as the default. */
        val DEFAULT_AGGRESSIVE = EscalationChain(
            enabled = true,
            steps = listOf(
                EscalationStep(EscalationStepAction.GENTLE_PING, delayMs = 0L),
                EscalationStep(
                    EscalationStepAction.STANDARD_ALERT,
                    delayMs = 2 * 60 * 1000L,
                    triggerTiers = setOf(UrgencyTier.MEDIUM, UrgencyTier.HIGH, UrgencyTier.CRITICAL)
                ),
                EscalationStep(
                    EscalationStepAction.LOUD_VIBRATE,
                    delayMs = 5 * 60 * 1000L,
                    triggerTiers = setOf(UrgencyTier.HIGH, UrgencyTier.CRITICAL)
                ),
                EscalationStep(
                    EscalationStepAction.FULL_SCREEN,
                    delayMs = 10 * 60 * 1000L,
                    triggerTiers = setOf(UrgencyTier.CRITICAL)
                )
            ),
            stopOnInteraction = true,
            maxAttempts = 4
        )
    }
}
