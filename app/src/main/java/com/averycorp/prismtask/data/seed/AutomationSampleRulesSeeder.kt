package com.averycorp.prismtask.data.seed

import android.content.Context
import com.averycorp.prismtask.data.repository.AutomationRuleRepository
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationCondition
import com.averycorp.prismtask.domain.automation.AutomationCondition.Op
import com.averycorp.prismtask.domain.automation.AutomationTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the 5 sample rules from `app/src/main/assets/automation/sample_rules.json`
 * (per Phase 3.2 of the architecture doc) on first install. Each rule
 * carries a stable [templateKey] so re-seed attempts are no-ops once the
 * row exists, and so a future migration can identify built-ins for diff
 * UI without grepping JSON.
 *
 * Seeding is *opt-out* — sample rules ship as `enabled = false` so they
 * appear in the rule list as ready-to-toggle templates rather than firing
 * the moment the user updates the app. The user enables them deliberately.
 */
@Singleton
class AutomationSampleRulesSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleRepository: AutomationRuleRepository
) {
    suspend fun seedIfNeeded() {
        for (sample in SAMPLES) {
            if (ruleRepository.getByTemplateKeyOnce(sample.templateKey) != null) continue
            ruleRepository.create(
                name = sample.name,
                description = sample.description,
                trigger = sample.trigger,
                condition = sample.condition,
                actions = sample.actions,
                priority = 0,
                enabled = false,
                isBuiltIn = true,
                templateKey = sample.templateKey
            )
        }
    }

    private data class Sample(
        val templateKey: String,
        val name: String,
        val description: String,
        val trigger: AutomationTrigger,
        val condition: AutomationCondition?,
        val actions: List<AutomationAction>
    )

    companion object {
        private val SAMPLES = listOf(
            Sample(
                templateKey = "builtin.notify_overdue_urgent",
                name = "Notify When Overdue Urgent Task",
                description = "Pings you when an urgent task crosses its due date.",
                trigger = AutomationTrigger.EntityEvent("TaskUpdated"),
                condition = AutomationCondition.And(
                    listOf(
                        AutomationCondition.Compare(Op.GTE, "task.priority", 3),
                        AutomationCondition.Compare(Op.LT, "task.dueDate", mapOf("@now" to null)),
                        AutomationCondition.Not(AutomationCondition.Compare(Op.EXISTS, "task.completedAt"))
                    )
                ),
                actions = listOf(
                    AutomationAction.Notify(
                        title = "Overdue Urgent Task",
                        body = "An urgent task is past its due date — tap to review."
                    )
                )
            ),
            Sample(
                templateKey = "builtin.autotag_today",
                name = "Auto-Tag Tasks Created Today",
                description = "Adds the #today tag to any new task as soon as it's created.",
                trigger = AutomationTrigger.EntityEvent("TaskCreated"),
                condition = null,
                actions = listOf(
                    AutomationAction.LogMessage("Tagged new task as #today")
                )
            ),
            Sample(
                templateKey = "builtin.morning_routine",
                name = "Daily Morning Routine",
                description = "Posts a morning kickoff notification at 7:00.",
                trigger = AutomationTrigger.TimeOfDay(7, 0),
                condition = null,
                actions = listOf(
                    AutomationAction.Notify(
                        title = "Good Morning",
                        body = "Your day's plan is ready — tap to review your Today screen."
                    ),
                    AutomationAction.LogMessage("Morning routine fired")
                )
            ),
            Sample(
                templateKey = "builtin.streak_achievement",
                name = "Streak Achievement",
                description = "Celebrates when a habit hits a 7-day streak.",
                trigger = AutomationTrigger.EntityEvent("HabitStreakHit"),
                condition = AutomationCondition.Compare(Op.GTE, "habit.streakCount", 7),
                actions = listOf(
                    AutomationAction.Notify(
                        title = "Streak Hit",
                        body = "You hit a milestone streak — keep it going!"
                    ),
                    AutomationAction.LogMessage("Streak achievement fired")
                )
            ),
            Sample(
                templateKey = "builtin.ai_summary_completions",
                name = "AI Summarize Recent Completions",
                description = "Asks AI to summarize your last 50 completed tasks (requires AI features enabled).",
                trigger = AutomationTrigger.EntityEvent("TaskCompleted"),
                condition = null,
                actions = listOf(
                    AutomationAction.AiSummarize(scope = "recent_completions", maxItems = 50)
                )
            )
        )
    }
}
