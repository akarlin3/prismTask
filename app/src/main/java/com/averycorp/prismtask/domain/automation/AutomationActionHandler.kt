package com.averycorp.prismtask.domain.automation

/**
 * Per-action-type handler. Implementations live in
 * `domain/automation/handlers/`. Engine resolves the right handler via
 * the action's [AutomationAction.type] discriminator.
 *
 * Failure isolation (§ A6): the engine wraps each `execute()` in a
 * try/catch. A handler returning [ActionResult.Error] does not halt the
 * action chain; the next action runs.
 */
interface AutomationActionHandler {
    val type: String
    suspend fun execute(action: AutomationAction, ctx: ExecutionContext): ActionResult
}

/**
 * Per-firing context shared by all handlers. Carries the trigger event,
 * the resolved entity (so handlers don't re-fetch from the DAO), the
 * rule itself (for log writes), and the chain lineage for cycle
 * detection.
 *
 * `lineage` is the set of rule ids already in the current execution
 * chain — engine adds the current rule before invoking handlers, and
 * checks for self-edges before queuing composed RuleFired emissions.
 */
data class ExecutionContext(
    val rule: com.averycorp.prismtask.data.local.entity.AutomationRuleEntity,
    val event: AutomationEvent,
    val evaluation: EvaluationContext,
    val depth: Int,
    val lineage: Set<Long>,
    val parentLogId: Long?
)

sealed class ActionResult {
    abstract val type: String
    data class Ok(override val type: String, val message: String? = null) : ActionResult()
    data class Skipped(override val type: String, val reason: String) : ActionResult()
    data class Error(override val type: String, val reason: String) : ActionResult()
}
