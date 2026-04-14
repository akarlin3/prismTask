package com.averycorp.prismtask.diagnostics

import com.averycorp.prismtask.data.diagnostics.DiagnosticLogger
import com.averycorp.prismtask.data.diagnostics.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLoggerTest {
    private lateinit var logger: DiagnosticLogger

    @Before
    fun setUp() {
        logger = DiagnosticLogger()
    }

    @Test
    fun `log entry stored in buffer correctly`() {
        logger.info("Nav", "Navigated to TodayScreen")

        val entries = logger.getEntries()
        assertEquals(1, entries.size)
        assertEquals("Nav", entries[0].tag)
        assertEquals(LogLevel.INFO, entries[0].level)
        assertEquals("Navigated to TodayScreen", entries[0].message)
        assertTrue(entries[0].timestamp > 0)
    }

    @Test
    fun `circular buffer evicts oldest entry when full`() {
        // Fill buffer beyond max
        repeat(DiagnosticLogger.MAX_ENTRIES + 1) { i ->
            logger.info("Test", "Entry $i")
        }

        val entries = logger.getEntries()
        assertEquals(DiagnosticLogger.MAX_ENTRIES, entries.size)
        // First entry should be "Entry 1" (Entry 0 was evicted)
        assertEquals("Entry 1", entries.first().message)
        assertEquals("Entry ${DiagnosticLogger.MAX_ENTRIES}", entries.last().message)
    }

    @Test
    fun `export format contains timestamp, tag, level, and message`() {
        logger.info("Nav", "Navigated to TodayScreen")
        logger.warn("Sync", "Sync failed: timeout")
        logger.error("Task", "Repository error")

        val text = logger.exportAsText()
        assertTrue(text.contains("[INFO]"))
        assertTrue(text.contains("[WARN]"))
        assertTrue(text.contains("[ERROR]"))
        assertTrue(text.contains("[Nav]"))
        assertTrue(text.contains("[Sync]"))
        assertTrue(text.contains("[Task]"))
        assertTrue(text.contains("Navigated to TodayScreen"))
        assertTrue(text.contains("Sync failed: timeout"))
        assertTrue(text.contains("Repository error"))
        assertTrue(text.contains("# PrismTask Diagnostic Log"))
        assertTrue(text.contains("No personal content is included"))
    }

    @Test
    fun `thread safety - concurrent writes from multiple coroutines`() = runBlocking {
        val jobs = (1..100).map { i ->
            launch(Dispatchers.Default) {
                logger.info("Thread", "Message $i")
            }
        }
        jobs.forEach { it.join() }

        val entries = logger.getEntries()
        assertEquals(100, entries.size)
    }

    @Test
    fun `empty buffer export returns header only`() {
        val text = logger.exportAsText()
        assertTrue(text.contains("# PrismTask Diagnostic Log"))
        assertTrue(text.contains("Entries: 0"))
    }

    @Test
    fun `clear removes all entries`() {
        logger.info("Test", "Entry 1")
        logger.info("Test", "Entry 2")
        assertEquals(2, logger.getEntryCount())

        logger.clear()
        assertEquals(0, logger.getEntryCount())
    }
}
