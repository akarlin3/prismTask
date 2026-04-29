package com.averycorp.prismtask.data.remote.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives two background sync triggers:
 *
 *  1. **Reactive reconnect** — fires `onTrigger("network_resumed")` once
 *     each time `isOnline` flips from `false` to `true`. The seeded
 *     value of the [StateFlow] is treated as the baseline; only later
 *     transitions trigger.
 *  2. **Periodic floor** — fires `onTrigger("periodic_${periodMs/1000}s")`
 *     every [periodMs] while `isOnline.value` is true and `isSignedIn()`
 *     returns true. Provides a safety net if a connectivity callback is
 *     missed and bounds worst-case staleness when the app is open but
 *     idle.
 *
 * Both checks gate on [isSignedIn] so a signed-out user never triggers a
 * sync. The caller's downstream `fullSync` should handle its own
 * `isSyncing` reentrancy guard — this driver intentionally does not.
 *
 * The driver does not own a scope; the caller passes the long-lived
 * scope it already maintains for sync work.
 */
internal class ReactiveSyncDriver(
    private val isOnline: StateFlow<Boolean>,
    private val isSignedIn: () -> Boolean,
    private val periodMs: Long,
    private val onTrigger: suspend (trigger: String) -> Unit
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous: Boolean? = null
            isOnline.collect { online ->
                val prior = previous
                previous = online
                if (prior == false && online && isSignedIn()) {
                    runCatching { onTrigger("network_resumed") }
                }
            }
        }
        scope.launch {
            val label = "periodic_${periodMs / 1000}s"
            while (isActive) {
                delay(periodMs)
                if (isOnline.value && isSignedIn()) {
                    runCatching { onTrigger(label) }
                }
            }
        }
    }
}
