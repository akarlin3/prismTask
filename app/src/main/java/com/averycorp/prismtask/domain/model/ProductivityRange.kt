package com.averycorp.prismtask.domain.model

/**
 * Range selector for the productivity score chart — mirrors the web PR #715
 * `RangeOption` (`'7d' | '30d' | '90d'`). Used by `TaskAnalyticsScreen`
 * productivity section (slice 3 of the port — see
 * `docs/audits/ANALYTICS_PR715_PORT_AUDIT.md`).
 */
enum class ProductivityRange(val days: Int, val label: String) {
    SEVEN_DAYS(7, "7 Days"),
    THIRTY_DAYS(30, "30 Days"),
    NINETY_DAYS(90, "90 Days")
}
