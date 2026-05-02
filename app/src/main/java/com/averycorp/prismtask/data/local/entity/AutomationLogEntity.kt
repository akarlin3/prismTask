package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per rule firing — first-class observability per
 * `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` § A8.
 *
 * Rows carry the trigger event payload, whether the condition matched
 * (`conditionPassed`), the per-action results JSON, errors, duration in
 * ms, and chain-context ([chainDepth] / [parentLogId]) so composed-trigger
 * lineage is reconstructible after the fact.
 *
 * Retention: rows older than 30 days are deleted by the daily reset
 * worker. Logs are local-only — they're observability, not state, and not
 * worth syncing.
 */
@Entity(
    tableName = "automation_logs",
    foreignKeys = [
        ForeignKey(
            entity = AutomationRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rule_id", "fired_at"]),
        Index(value = ["fired_at"])
    ]
)
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "rule_id")
    val ruleId: Long,
    @ColumnInfo(name = "fired_at")
    val firedAt: Long,
    @ColumnInfo(name = "trigger_event_json")
    val triggerEventJson: String? = null,
    @ColumnInfo(name = "condition_passed")
    val conditionPassed: Boolean,
    @ColumnInfo(name = "actions_executed_json")
    val actionsExecutedJson: String? = null,
    @ColumnInfo(name = "errors_json")
    val errorsJson: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "chain_depth", defaultValue = "0")
    val chainDepth: Int = 0,
    @ColumnInfo(name = "parent_log_id")
    val parentLogId: Long? = null
)
