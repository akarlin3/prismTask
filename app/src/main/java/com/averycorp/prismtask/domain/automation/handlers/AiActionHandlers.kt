package com.averycorp.prismtask.domain.automation.handlers

import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.remote.api.AiFeatureGateInterceptor
import com.averycorp.prismtask.data.remote.api.AutomationCompleteRequest
import com.averycorp.prismtask.data.remote.api.AutomationSummarizeRequest
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.domain.automation.ActionResult
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationActionHandler
import com.averycorp.prismtask.domain.automation.AutomationEvent
import com.averycorp.prismtask.domain.automation.ExecutionContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `ai.complete` and `ai.summarize` action handlers — routed through the
 * backend `/api/v1/ai/automation/{action}` endpoints, which inherit the
 * existing `/ai/` prefix entry in [AiFeatureGateInterceptor.AI_PATH_PREFIXES]
 * (no prefix-list update required — see § A5 of the architecture doc).
 *
 * The handlers double-check the master AI toggle locally
 * (defense-in-depth — the OkHttp interceptor would short-circuit a 451
 * anyway, but checking here lets us log a meaningful "AI gate disabled"
 * skip reason instead of an error).
 *
 * Network-result mapping:
 *   * 2xx                            -> [ActionResult.Ok]
 *   * 451 (AI gate)                  -> [ActionResult.Skipped]
 *   * any other failure              -> [ActionResult.Error]
 */
@Singleton
class AiCompleteActionHandler @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val api: PrismTaskApi
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
        return try {
            val response = api.automationComplete(
                AutomationCompleteRequest(
                    prompt = ai.prompt,
                    context = buildEventContext(ctx, ai.targetField)
                )
            )
            val text = response.text.trim()
            ActionResult.Ok(
                type,
                if (text.isEmpty()) "ai.complete returned empty text" else "ai.complete: ${text.take(120)}"
            )
        } catch (e: HttpException) {
            mapHttpFailure(type, e)
        } catch (e: Exception) {
            ActionResult.Error(type, "ai.complete failed: ${e.message ?: e::class.java.simpleName}")
        }
    }
}

@Singleton
class AiSummarizeActionHandler @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val api: PrismTaskApi
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
        return try {
            val response = api.automationSummarize(
                AutomationSummarizeRequest(
                    scope = ai.scope,
                    maxItems = ai.maxItems,
                    context = buildEventContext(ctx, targetField = null)
                )
            )
            val summary = response.summary.trim()
            ActionResult.Ok(
                type,
                if (summary.isEmpty()) "ai.summarize returned empty summary" else "ai.summarize: ${summary.take(120)}"
            )
        } catch (e: HttpException) {
            mapHttpFailure(type, e)
        } catch (e: Exception) {
            ActionResult.Error(type, "ai.summarize failed: ${e.message ?: e::class.java.simpleName}")
        }
    }
}

/**
 * Map an HTTP failure to the right [ActionResult]. 451 (AI gate) is the
 * one status code that should *not* hard-fail the chain — the user has
 * deliberately opted out of AI egress and we want a clean "skipped"
 * entry on the firing log rather than an error stack.
 */
private fun mapHttpFailure(type: String, e: HttpException): ActionResult =
    when (e.code()) {
        AiFeatureGateInterceptor.HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS ->
            ActionResult.Skipped(type, "AI features disabled (server-side 451)")
        else -> ActionResult.Error(type, "$type HTTP ${e.code()}: ${e.message()}")
    }

/**
 * Compact, opaque dict the backend forwards verbatim into the Haiku
 * prompt. Carries the trigger event's discriminator + occurredAt + the
 * primary entity id so the AI can ground its response in *which* entity
 * fired the rule, without us shipping the whole entity payload over the
 * wire (defensive on PII surface).
 */
private fun buildEventContext(
    ctx: ExecutionContext,
    targetField: String?
): Map<String, Any?> {
    val event = ctx.event
    val base = mutableMapOf<String, Any?>(
        "trigger_kind" to event.kind(),
        "occurred_at_ms" to event.occurredAt,
        "rule_id" to ctx.rule.id
    )
    when (event) {
        is AutomationEvent.TaskCreated -> base["task_id"] = event.taskId
        is AutomationEvent.TaskUpdated -> {
            base["task_id"] = event.taskId
            if (event.changedFields.isNotEmpty()) {
                base["changed_fields"] = event.changedFields.toList()
            }
        }
        is AutomationEvent.TaskCompleted -> base["task_id"] = event.taskId
        is AutomationEvent.TaskDeleted -> base["task_id"] = event.taskId
        is AutomationEvent.HabitCompleted -> {
            base["habit_id"] = event.habitId
            base["date"] = event.date
        }
        is AutomationEvent.HabitStreakHit -> {
            base["habit_id"] = event.habitId
            base["streak"] = event.streak
        }
        is AutomationEvent.MedicationLogged -> {
            base["medication_id"] = event.medicationId
            base["slot_key"] = event.slotKey
        }
        is AutomationEvent.TimeTick -> {
            base["hour"] = event.hour
            base["minute"] = event.minute
        }
        is AutomationEvent.ManualTrigger -> base["triggered_rule_id"] = event.ruleId
        is AutomationEvent.RuleFired -> {
            base["fired_rule_id"] = event.ruleId
            base["parent_log_id"] = event.parentLogId
        }
    }
    if (targetField != null) base["target_field"] = targetField
    return base
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
