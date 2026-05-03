package com.averycorp.prismtask.domain.automation.handlers

import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.domain.automation.ActionResult
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationActionHandler
import com.averycorp.prismtask.domain.automation.ExecutionContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `ai.complete` and `ai.summarize` action handlers — routed through the
 * backend `/api/v1/ai/automation/{action}` endpoints, which inherit the
 * existing `/ai/` prefix entry in [AiFeatureGateInterceptor.AI_PATH_PREFIXES]
 * (no prefix-list update required — see § A5 of the architecture doc).
 *
 * v1 behavior: the handlers double-check the master AI toggle locally
 * (defense-in-depth — the OkHttp interceptor would short-circuit a 451
 * anyway, but checking here lets us log a meaningful "AI gate disabled"
 * skip reason instead of an error).
 *
 * The actual Anthropic round-trip ships in a follow-up PR alongside the
 * backend `/ai/automation/{action}` routes; the action type is registered so
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
 * `apply.batch` handler — converts each [Map] in
 * [AutomationAction.ApplyBatch.mutations] into a
 * [com.averycorp.prismtask.data.remote.api.ProposedMutationResponse] and
 * delegates to [com.averycorp.prismtask.data.repository.BatchOperationsRepository.applyBatch].
 *
 * Bypasses the Anthropic-touching `parseCommand` round-trip — the engine
 * supplies the mutation plan directly, so this handler is *not*
 * AI-gated even though the underlying repository can be when called via
 * its NLP entry point.
 *
 * Expected shape per map entry:
 *  - `entityType`: TASK / HABIT / PROJECT / MEDICATION
 *  - `entityId`: stringified row id
 *  - `mutationType`: COMPLETE / RESCHEDULE / DELETE / etc. (see [BatchMutationType])
 *  - any other keys land in `proposedNewValues` verbatim
 */
@Singleton
class ApplyBatchActionHandler @Inject constructor(
    private val batchOperationsRepository: com.averycorp.prismtask.data.repository.BatchOperationsRepository
) : AutomationActionHandler {
    override val type: String = "apply.batch"

    override suspend fun execute(
        action: AutomationAction,
        ctx: ExecutionContext
    ): ActionResult {
        val batch = action as? AutomationAction.ApplyBatch
            ?: return ActionResult.Error(type, "wrong action shape")
        if (batch.mutations.isEmpty()) {
            return ActionResult.Skipped(type, "no mutations to apply")
        }
        val proposed = batch.mutations.mapNotNull { m ->
            val entityType = m["entityType"] as? String ?: return@mapNotNull null
            val entityId = m["entityId"]?.toString() ?: return@mapNotNull null
            val mutationType = m["mutationType"] as? String ?: return@mapNotNull null
            val proposedValues = m.filterKeys { it !in setOf("entityType", "entityId", "mutationType") }
            com.averycorp.prismtask.data.remote.api.ProposedMutationResponse(
                entityType = entityType,
                entityId = entityId,
                mutationType = mutationType,
                proposedNewValues = proposedValues,
                humanReadableDescription = "automation rule ${ctx.rule.id}: $mutationType $entityType $entityId"
            )
        }
        if (proposed.isEmpty()) {
            return ActionResult.Skipped(type, "no well-formed mutations after parse")
        }
        val result = batchOperationsRepository.applyBatch(
            commandText = "automation:rule_${ctx.rule.id}",
            mutations = proposed
        )
        return ActionResult.Ok(
            type,
            "applied ${result.appliedCount} of ${proposed.size} (batch=${result.batchId})"
        )
    }
}
