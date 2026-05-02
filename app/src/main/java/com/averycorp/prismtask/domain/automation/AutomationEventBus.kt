package com.averycorp.prismtask.domain.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized event bus (CAUSE-α from § A3 of the architecture doc) — a
 * single [MutableSharedFlow] that every entity repository writes to and
 * the [AutomationEngine] subscribes to.
 *
 * Buffer is [BUFFER_CAPACITY] events with [BufferOverflow.DROP_OLDEST] —
 * if the engine collector falls behind during a burst (mass import,
 * batch op apply), older events get dropped rather than blocking write
 * sites. The trade is acceptable for this use case: rules are best-effort,
 * not transactional.
 */
@Singleton
class AutomationEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AutomationEvent>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<AutomationEvent> = _events.asSharedFlow()

    /** Non-blocking emit; safe to call from any coroutine context. */
    fun emit(event: AutomationEvent): Boolean = _events.tryEmit(event)

    companion object {
        const val BUFFER_CAPACITY = 64
    }
}
