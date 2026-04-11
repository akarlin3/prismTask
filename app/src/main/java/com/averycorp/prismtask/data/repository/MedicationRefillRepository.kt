package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.MedicationRefillDao
import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import com.averycorp.prismtask.domain.usecase.RefillCalculator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for medication refill metadata (v1.4.0 V10).
 *
 * Wraps the DAO with convenience helpers that apply the pure-function
 * [RefillCalculator] so callers can observe rows and apply a daily dose
 * or refill without having to reach into the use case layer.
 */
@Singleton
class MedicationRefillRepository @Inject constructor(
    private val dao: MedicationRefillDao
) {

    fun observeAll(): Flow<List<MedicationRefillEntity>> = dao.observeAll()

    suspend fun getAll(): List<MedicationRefillEntity> = dao.getAll()

    suspend fun getByName(name: String): MedicationRefillEntity? = dao.getByName(name)

    suspend fun upsert(refill: MedicationRefillEntity): Long = dao.upsert(refill)

    suspend fun applyDailyDose(refill: MedicationRefillEntity) {
        dao.update(RefillCalculator.applyDailyDose(refill))
    }

    suspend fun applyRefill(refill: MedicationRefillEntity, newSupply: Int) {
        dao.update(RefillCalculator.applyRefill(refill, newSupply))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
