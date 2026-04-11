package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ReminderProfileDao
import com.averycorp.prismtask.data.local.entity.ReminderProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderProfileRepository @Inject constructor(
    private val dao: ReminderProfileDao
) {
    fun getAll(): Flow<List<ReminderProfileEntity>> = dao.getAll()

    suspend fun getById(id: Long): ReminderProfileEntity? = dao.getById(id)

    suspend fun insert(profile: ReminderProfileEntity): Long = dao.insert(profile)

    suspend fun update(profile: ReminderProfileEntity) = dao.update(profile)

    suspend fun delete(profile: ReminderProfileEntity) = dao.delete(profile)

    suspend fun seedBuiltInsIfEmpty() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val hour = 60L * 60 * 1000
        val minute = 60L * 1000
        BUILT_INS.forEach { (name, offsets, esc, interval) ->
            dao.insert(
                ReminderProfileEntity(
                    name = name,
                    offsetsCsv = ReminderProfileEntity.encodeOffsets(offsets),
                    escalation = esc,
                    escalationIntervalMinutes = interval,
                    isBuiltIn = true,
                    createdAt = now
                )
            )
        }
    }

    companion object {
        private val DAY = 24L * 60 * 60 * 1000
        private val HOUR = 60L * 60 * 1000

        /**
         * (name, offsets in millis before due, escalation enabled, escalation interval minutes)
         */
        val BUILT_INS: List<Quadruple<String, List<Long>, Boolean, Int?>> = listOf(
            Quadruple("Gentle", listOf(DAY, 0L), false, null),
            Quadruple("Aggressive", listOf(DAY, HOUR, 0L), true, 15),
            Quadruple("Minimal", listOf(0L), false, null)
        )

        data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D) {
            operator fun component1() = a
            operator fun component2() = b
            operator fun component3() = c
            operator fun component4() = d
        }
    }
}
