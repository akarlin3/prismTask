package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted weekly review (v1.4.0 V6).
 *
 * Each row is one week of aggregated stats plus the optional AI-generated
 * narrative (Premium only). Both payloads are JSON blobs so the table
 * schema doesn't have to track every metric the aggregator emits.
 *
 * The `(week_start_date)` index is unique so a re-run of the aggregation
 * worker over the same week doesn't create duplicate rows.
 */
@Entity(
    tableName = "weekly_reviews",
    indices = [Index(value = ["week_start_date"], unique = true)]
)
data class WeeklyReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Midnight-normalized Monday of the review week, in millis. */
    @ColumnInfo(name = "week_start_date")
    val weekStartDate: Long,
    @ColumnInfo(name = "metrics_json")
    val metricsJson: String,
    @ColumnInfo(name = "ai_insights_json")
    val aiInsightsJson: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
