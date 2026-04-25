package com.averycorp.prismtask.diagnostics

import com.averycorp.prismtask.data.diagnostics.MigrationInstrumentor
import com.averycorp.prismtask.domain.model.telemetry.MigrationTelemetryEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests cover the in-memory event surface — Firebase access
 * always fails on JVM (no `FirebaseApp`), which exercises the
 * pre-init buffer path that real-world cold-boot installs hit.
 */
class MigrationInstrumentorTest {
    @Before
    fun setUp() {
        MigrationInstrumentor.reset()
    }

    @After
    fun tearDown() {
        MigrationInstrumentor.reset()
    }

    @Test
    fun `successful migration buffers Started + Completed events`() {
        MigrationInstrumentor.run(from = 60, to = 61, dbSizeBytes = 1234L) {
            // simulated migration body
        }

        val events = MigrationInstrumentor.pendingSnapshot()
        assertEquals(2, events.size)
        val started = events[0] as MigrationTelemetryEvent.Started
        assertEquals(60, started.versionFrom)
        assertEquals(61, started.versionTo)
        assertEquals(1234L, started.dbSizeBytes)
        val completed = events[1] as MigrationTelemetryEvent.Completed
        assertEquals(60, completed.versionFrom)
        assertEquals(61, completed.versionTo)
        assertTrue("duration_ms must be non-negative", completed.durationMs >= 0)
        assertTrue("cumulative_ms must be non-negative", completed.cumulativeMs >= 0)
    }

    @Test
    fun `failed migration buffers Failed event with truncated message and rethrows`() {
        val cause = RuntimeException("a".repeat(500))
        try {
            MigrationInstrumentor.run<Unit>(from = 53, to = 54, dbSizeBytes = 5_555L) {
                throw cause
            }
            fail("Expected the original exception to propagate")
        } catch (caught: RuntimeException) {
            assertSame("Original exception must propagate untouched", cause, caught)
        }

        val events = MigrationInstrumentor.pendingSnapshot()
        assertEquals(2, events.size)
        val failed = events[1] as MigrationTelemetryEvent.Failed
        assertEquals(53, failed.versionFrom)
        assertEquals(54, failed.versionTo)
        assertEquals(5_555L, failed.dbSizeBytes)
        assertEquals("java.lang.RuntimeException", failed.exceptionClass)
        assertEquals(120, failed.messageFirst120.length)
        assertNull(
            "lastCompletedStep must be null on the very first migration",
            failed.lastCompletedStep
        )
    }

    @Test
    fun `failed migration after a success records lastCompletedStep`() {
        MigrationInstrumentor.run(from = 60, to = 61, dbSizeBytes = 1L) { }
        try {
            MigrationInstrumentor.run<Unit>(from = 61, to = 62, dbSizeBytes = 1L) {
                throw IllegalStateException("boom")
            }
        } catch (_: IllegalStateException) {
            // expected
        }

        val failed = MigrationInstrumentor.pendingSnapshot()
            .filterIsInstance<MigrationTelemetryEvent.Failed>()
            .last()
        assertEquals("60->61", failed.lastCompletedStep)
    }

    @Test
    fun `cumulativeMs adds across consecutive successful migrations`() {
        MigrationInstrumentor.run(from = 60, to = 61, dbSizeBytes = 0L) {
            Thread.sleep(2)
        }
        MigrationInstrumentor.run(from = 61, to = 62, dbSizeBytes = 0L) {
            Thread.sleep(2)
        }

        val completed = MigrationInstrumentor.pendingSnapshot()
            .filterIsInstance<MigrationTelemetryEvent.Completed>()
        assertEquals(2, completed.size)
        assertTrue(
            "cumulativeMs must be monotonically non-decreasing",
            completed[1].cumulativeMs >= completed[0].cumulativeMs
        )
    }

    @Test
    fun `emitPostV54IfApplicable below v54 emits nothing`() {
        MigrationInstrumentor.emitPostV54IfApplicable(currentVersion = 53, shimAgeDays = 7L)
        assertTrue(MigrationInstrumentor.pendingSnapshot().isEmpty())
    }

    @Test
    fun `emitPostV54IfApplicable at or above v54 emits exactly once per launch`() {
        MigrationInstrumentor.emitPostV54IfApplicable(currentVersion = 63, shimAgeDays = 3L)
        MigrationInstrumentor.emitPostV54IfApplicable(currentVersion = 63, shimAgeDays = 9L)

        val postV54 = MigrationInstrumentor.pendingSnapshot()
            .filterIsInstance<MigrationTelemetryEvent.PostV54Install>()
        assertEquals(1, postV54.size)
        assertEquals(3L, postV54[0].shimAgeDays)
    }

    @Test
    fun `pending buffer caps at 16 events and drops oldest first`() {
        // 10 successful migrations = 20 events (Started + Completed each).
        // The buffer cap (16) must hold the most recent 16, dropping the 4 oldest.
        repeat(10) { i ->
            MigrationInstrumentor.run(from = i, to = i + 1, dbSizeBytes = 0L) { }
        }
        val events = MigrationInstrumentor.pendingSnapshot()
        assertEquals(16, events.size)
        // The oldest retained event should be from migration index 2 (Started).
        val firstStarted = events.first() as MigrationTelemetryEvent.Started
        assertEquals(2, firstStarted.versionFrom)
    }

    @Test
    fun `reset clears pending buffer and post-v54 latch`() {
        MigrationInstrumentor.run(from = 60, to = 61, dbSizeBytes = 0L) { }
        MigrationInstrumentor.emitPostV54IfApplicable(currentVersion = 63, shimAgeDays = 1L)
        assertTrue(MigrationInstrumentor.pendingSnapshot().isNotEmpty())

        MigrationInstrumentor.reset()

        assertTrue(MigrationInstrumentor.pendingSnapshot().isEmpty())
        // Post-v54 should re-fire after reset
        MigrationInstrumentor.emitPostV54IfApplicable(currentVersion = 63, shimAgeDays = 5L)
        val postV54 = MigrationInstrumentor.pendingSnapshot()
            .filterIsInstance<MigrationTelemetryEvent.PostV54Install>()
            .singleOrNull()
        assertNotNull(postV54)
    }
}
