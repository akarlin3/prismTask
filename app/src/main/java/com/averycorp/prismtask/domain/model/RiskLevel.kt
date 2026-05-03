package com.averycorp.prismtask.domain.model

/**
 * Severity of a [com.averycorp.prismtask.data.local.entity.ProjectRiskEntity].
 * Stored as the enum name in the `project_risks.level` column.
 *
 * Unknown / null storage values are mapped to [MEDIUM] by [fromStorage].
 * Added in v1.8.x as part of the PrismTask-timeline-class scope (PR-1).
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromStorage(value: String?): RiskLevel = when (value) {
            "LOW" -> LOW
            "HIGH" -> HIGH
            else -> MEDIUM
        }
    }
}
