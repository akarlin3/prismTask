package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.WeeklyReviewDao
import com.averycorp.prismtask.data.local.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for historical weekly reviews (v1.4.0 V6).
 *
 * `metricsJson` is the serialized [com.averycorp.prismtask.domain.usecase.WeeklyReviewStats]
 * snapshot and `aiInsightsJson` is the optional Claude-generated wins/
 * misses/suggestions blob. The repository doesn't crack either payload —
 * callers pass pre-serialized strings.
 */
@Singleton
class WeeklyReviewRepository
@Inject
constructor(
    private val dao: WeeklyReviewDao
) {
    suspend fun save(
        weekStart: Long,
        metricsJson: String,
        aiInsightsJson: String? = null
    ): Long {
        val existing = dao.getByWeek(weekStart)
        val row = WeeklyReviewEntity(
            id = existing?.id ?: 0,
            weekStartDate = weekStart,
            metricsJson = metricsJson,
            aiInsightsJson = aiInsightsJson,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        return dao.upsert(row)
    }

    suspend fun getForWeek(weekStart: Long): WeeklyReviewEntity? = dao.getByWeek(weekStart)

    suspend fun getMostRecent(): WeeklyReviewEntity? = dao.getMostRecent()

    fun observeAll(): Flow<List<WeeklyReviewEntity>> = dao.observeAll()

    suspend fun delete(id: Long) = dao.delete(id)
}
