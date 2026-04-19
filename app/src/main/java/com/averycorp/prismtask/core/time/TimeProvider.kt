package com.averycorp.prismtask.core.time

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over "what time is it now" so callers of [DayBoundary] and other
 * time-sensitive logic can be tested without monkeypatching the clock.
 *
 * Production code binds [SystemTimeProvider] via Hilt. Tests can substitute a
 * fixed implementation.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
