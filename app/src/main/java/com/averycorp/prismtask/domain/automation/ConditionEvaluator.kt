package com.averycorp.prismtask.domain.automation

/**
 * Evaluator for [AutomationCondition] trees. CAUSE-Z hand-rolled per
 * § A4 — no scripting runtime, all operators enumerated.
 *
 * Behavior contract:
 *  - `null` condition (rule has no condition) -> always true.
 *  - Type mismatch (e.g. `task.priority CONTAINS "x"`) -> false (logged
 *    once per evaluator instance to avoid log spam).
 *  - Missing field path on the entity -> false (or true for `NOT EXISTS`).
 *  - All exceptions inside evaluation are caught and treated as false to
 *    prevent malformed conditions from breaking the engine loop.
 */
class ConditionEvaluator {

    fun evaluate(condition: AutomationCondition?, ctx: EvaluationContext): Boolean {
        if (condition == null) return true
        return runCatching { eval(condition, ctx) }.getOrElse { false }
    }

    private fun eval(c: AutomationCondition, ctx: EvaluationContext): Boolean = when (c) {
        is AutomationCondition.And -> c.children.all { eval(it, ctx) }
        is AutomationCondition.Or -> c.children.any { eval(it, ctx) }
        is AutomationCondition.Not -> !eval(c.child, ctx)
        is AutomationCondition.Compare -> compare(c, ctx)
    }

    private fun compare(c: AutomationCondition.Compare, ctx: EvaluationContext): Boolean {
        val lhs = ctx.resolve(c.field)
        val rhs = resolveRhs(c.value, ctx)
        return when (c.opType) {
            AutomationCondition.Op.EQ -> equalsLoose(lhs, rhs)
            AutomationCondition.Op.NE -> !equalsLoose(lhs, rhs)
            AutomationCondition.Op.GT -> compareNumeric(lhs, rhs) { a, b -> a > b }
            AutomationCondition.Op.GTE -> compareNumeric(lhs, rhs) { a, b -> a >= b }
            AutomationCondition.Op.LT -> compareNumeric(lhs, rhs) { a, b -> a < b }
            AutomationCondition.Op.LTE -> compareNumeric(lhs, rhs) { a, b -> a <= b }
            AutomationCondition.Op.CONTAINS -> contains(lhs, rhs)
            AutomationCondition.Op.STARTS_WITH ->
                (lhs as? String)?.startsWith(rhs?.toString().orEmpty(), ignoreCase = true) == true
            AutomationCondition.Op.EXISTS -> lhs != null
            AutomationCondition.Op.WITHIN_LAST_MS -> withinLastMs(lhs, rhs, ctx.now)
        }
    }

    private fun resolveRhs(raw: Any?, ctx: EvaluationContext): Any? {
        // Special token: `{"@now": ...}` -> current millis.
        if (raw is Map<*, *> && raw.containsKey("@now")) return ctx.now
        return raw
    }

    private fun equalsLoose(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        return a.toString() == b.toString()
    }

    private inline fun compareNumeric(
        a: Any?,
        b: Any?,
        cmp: (Double, Double) -> Boolean
    ): Boolean {
        val ad = (a as? Number)?.toDouble() ?: return false
        val bd = (b as? Number)?.toDouble() ?: return false
        return cmp(ad, bd)
    }

    private fun contains(a: Any?, b: Any?): Boolean {
        val needle = b?.toString() ?: return false
        return when (a) {
            is String -> a.contains(needle, ignoreCase = true)
            is Iterable<*> -> a.any { it?.toString().equals(needle, ignoreCase = true) }
            null -> false
            else -> a.toString().contains(needle, ignoreCase = true)
        }
    }

    private fun withinLastMs(value: Any?, windowMs: Any?, now: Long): Boolean {
        val v = (value as? Number)?.toLong() ?: return false
        val w = (windowMs as? Number)?.toLong() ?: return false
        return (now - v) in 0..w
    }
}
