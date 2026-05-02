package com.averycorp.prismtask.domain.automation.handlers

/**
 * `ai.complete` and `ai.summarize` action handlers — routed through the
 * backend `/api/v1/ai/automation/*` endpoints, which inherit the existing
 * `/ai/` prefix entry in [AiFeatureGateInterceptor.AI_PATH_PREFIXES] (no
 * prefix-list update required — see § A5 of the architecture doc).
 *
 * v1 behavior: the handlers double-check the master AI toggle locally
 * (defense-in-depth — the OkHttp interceptor would short-circuit a 451
 * anyway, but checking here lets us log a meaningful "AI gate disabled"
 * skip reason instead of an error).
 *
 * The actual Anthropic round-trip ships in a follow-up PR alongside the
 * backend `/ai/automation/*` routes; the action type is registered so
 * sample rules importing it parse + log cleanly, surfacing as a "Skipped:
 * backend endpoint pending" rather than a parse error.
 */
@Singleton
class AiCompleteActionHandler @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) : AutomationActionHandler {
    override val type: String = "ai.complete"

    override suspend fun execute(
        action: AutomationAction,
        ctx: ExecutionContext
    ): ActionResult {
        val ai = action as? AutomationAction.AiComplete
            ?: return ActionResult.Error(type, "wrong action shape")
        if (!userPreferencesDataStore.isAiFeaturesEnabledBlocking()) {
            return ActionResult.Skipped(type, "AI features disabled by user")
        }
        return ActionResult.Skipped(
            type,
            "ai.complete backend endpoint pending (would prompt: \"${ai.prompt.take(40)}…\")"
        )
    }
}

@Singleton
class AiSummarizeActionHandler @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) : AutomationActionHandler {
    override val type: String = "ai.summarize"

    override suspend fun execute(
        action: AutomationAction,
        ctx: ExecutionContext
    ): ActionResult {
        val ai = action as? AutomationAction.AiSummarize
            ?: return ActionResult.Error(type, "wrong action shape")
        if (!userPreferencesDataStore.isAiFeaturesEnabledBlocking()) {
            return ActionResult.Skipped(type, "AI features disabled by user")
        }
        return ActionResult.Skipped(
            type,
            "ai.summarize backend endpoint pending (scope=${ai.scope}, max=${ai.maxItems})"
        )
    }
}

/**
 * `apply.batch` handler — v1 stub. Threading [BatchOperationsRepository]
 * through here introduces a circular DI hazard (BatchOps depends on the
 * Anthropic-touching `parseCommand`; the engine should be able to apply
 * pre-built mutations *without* a parse round-trip, which means a small
 * `applyBatchSynthetic` extraction). Tracked for v1.1 alongside the rule
 * edit screen, where the user is in the loop on what mutations get
 * applied.
 */
@Singleton
class ApplyBatchActionHandler @Inject constructor() : AutomationActionHandler {
    override val type: String = "apply.batch"

    override suspend fun execute(
        action: AutomationAction,
        ctx: ExecutionContext
    ): ActionResult = ActionResult.Skipped(
        type,
        "apply.batch deferred to v1.1 — needs BatchOperationsRepository.applyBatchSynthetic extraction"
    )
}
