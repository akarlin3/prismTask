package com.averycorp.prismtask.data.remote.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage for [ReactiveSyncDriver]. Uses [runTest]'s virtual
 * time so the 30-second periodic loop can be exercised without real
 * waits, and a [MutableStateFlow] stand-in for the connectivity feed so
 * online/offline transitions are deterministic. The driver is started
 * on `backgroundScope` so its infinite periodic loop is cancelled when
 * each test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactiveSyncDriverTest {
    @Test
    fun `false to true transition fires network_resumed`() = runTest {
        val isOnline = MutableStateFlow(false)
        val triggers = mutableListOf<String>()
        ReactiveSyncDriver(
            isOnline = isOnline,
            isSignedIn = { true },
            periodMs = 30_000L,
            onTrigger = { triggers += it }
        ).start(backgroundScope)
        runCurrent()

        isOnline.value = true
        runCurrent()

        assertEquals(listOf("network_resumed"), triggers)
    }

    @Test
    fun `initial true seed does not fire`() = runTest {
        val isOnline = MutableStateFlow(true)
        val triggers = mutableListOf<String>()
        ReactiveSyncDriver(
            isOnline = isOnline,
            isSignedIn = { true },
            periodMs = 30_000L,
            onTrigger = { triggers += it }
        ).start(backgroundScope)
        runCurrent()

        assertTrue("Initial true seed must not be treated as a transition", triggers.isEmpty())
    }

    @Test
    fun `signed-out user suppresses reconnect trigger`() = runTest {
        val isOnline = MutableStateFlow(false)
        var signedIn = false
        val triggers = mutableListOf<String>()
        ReactiveSyncDriver(
            isOnline = isOnline,
            isSignedIn = { signedIn },
            periodMs = 30_000L,
            onTrigger = { triggers += it }
        ).start(backgroundScope)
        runCurrent()

        isOnline.value = true
        runCurrent()
        assertTrue("Signed-out reconnect must not fire", triggers.isEmpty())

        signedIn = true
        isOnline.value = false
        runCurrent()
        isOnline.value = true
        runCurrent()
        assertEquals(listOf("network_resumed"), triggers)
    }

    @Test
    fun `periodic trigger fires every periodMs while online`() = runTest {
        val isOnline = MutableStateFlow(true)
        val triggers = mutableListOf<String>()
        ReactiveSyncDriver(
            isOnline = isOnline,
            isSignedIn = { true },
            periodMs = 30_000L,
            onTrigger = { triggers += it }
        ).start(backgroundScope)
        runCurrent()

        advanceTimeBy(29_000L)
        runCurrent()
        assertTrue("No tick before the first 30 s elapse", triggers.isEmpty())

        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(listOf("periodic_30s"), triggers)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(listOf("periodic_30s", "periodic_30s"), triggers)
    }

    @Test
    fun `periodic trigger does not fire while offline`() = runTest {
        val isOnline = MutableStateFlow(false)
        val triggers = mutableListOf<String>()
        ReactiveSyncDriver(
            isOnline = isOnline,
            isSignedIn = { true },
            periodMs = 30_000L,
            onTrigger = { triggers += it }
        ).start(backgroundScope)
        runCurrent()

        advanceTimeBy(120_000L)
        runCurrent()

        assertTrue("Offline ticks must be skipped", triggers.isEmpty())
    }

    @Test
    fun `trigger exceptions do not break the loop`() = runTest {
        val isOnline = MutableStateFlow(true)
        var calls = 0
        ReactiveSyncDriver(
            isOnline = isOnline,
            isSignedIn = { true },
            periodMs = 30_000L,
            onTrigger = {
                calls++
                error("simulated sync failure")
            }
        ).start(backgroundScope)
        runCurrent()

        advanceTimeBy(31_000L)
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()

        assertEquals("Loop must keep ticking after a failure", 2, calls)
    }
}
