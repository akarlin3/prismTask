package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.usecase.VoiceCommand
import com.averycorp.prismtask.domain.usecase.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VoiceCommandParser]. Covers pattern matching for every
 * command type, rejection of plain text, fuzzy task-name matching, and
 * the date extraction path used by "reschedule ... to ..." commands.
 */
class VoiceCommandParserTest {
    private val parser = VoiceCommandParser()

    // ----- pattern matching -----

    @Test
    fun `plain task text returns null`() {
        assertNull(parser.parseCommand("buy groceries tomorrow"))
        assertNull(parser.parseCommand("write the report"))
    }

    @Test
    fun `complete patterns`() {
        val cmd = parser.parseCommand("complete buy groceries") as VoiceCommand.CompleteTask
        assertEquals("buy groceries", cmd.query)

        val cmd2 = parser.parseCommand("Mark write report done") as VoiceCommand.CompleteTask
        assertEquals("write report", cmd2.query)

        val cmd3 = parser.parseCommand("finished the dishes") as VoiceCommand.CompleteTask
        assertEquals("dishes", cmd3.query)
    }

    @Test
    fun `delete patterns`() {
        val cmd = parser.parseCommand("delete old task") as VoiceCommand.DeleteTask
        assertEquals("old task", cmd.query)
    }

    @Test
    fun `reschedule patterns extract both query and date`() {
        val cmd = parser.parseCommand("reschedule buy milk to tomorrow")
            as VoiceCommand.RescheduleTask
        assertEquals("buy milk", cmd.query)
        assertEquals("tomorrow", cmd.dateText)

        val cmd2 = parser.parseCommand("move write report until next friday")
            as VoiceCommand.RescheduleTask
        assertEquals("write report", cmd2.query)
        assertEquals("next friday", cmd2.dateText)
    }

    @Test
    fun `start and stop timer`() {
        val start = parser.parseCommand("start timer on write report")
            as VoiceCommand.StartTimer
        assertEquals("write report", start.query)

        assertTrue(parser.parseCommand("stop timer") is VoiceCommand.StopTimer)
    }

    @Test
    fun `whats next and task count and focus`() {
        assertTrue(parser.parseCommand("what's next") is VoiceCommand.WhatsNext)
        assertTrue(parser.parseCommand("Whats next?") is VoiceCommand.WhatsNext)
        assertTrue(parser.parseCommand("how many tasks today") is VoiceCommand.TaskCount)
        assertTrue(parser.parseCommand("start focus session") is VoiceCommand.StartFocus)
    }

    @Test
    fun `filler words are stripped`() {
        assertTrue(parser.parseCommand("hey what's next") is VoiceCommand.WhatsNext)
        val cmd = parser.parseCommand("please delete old task") as VoiceCommand.DeleteTask
        assertEquals("old task", cmd.query)
    }

    @Test
    fun `exit voice mode`() {
        assertTrue(parser.parseCommand("exit") is VoiceCommand.ExitVoiceMode)
        assertTrue(parser.parseCommand("exit voice mode") is VoiceCommand.ExitVoiceMode)
        assertTrue(parser.parseCommand("stop listening") is VoiceCommand.ExitVoiceMode)
    }

    // ----- fuzzy matching -----

    @Test
    fun `fuzzy match returns exact match first`() {
        val tasks = listOf("Buy milk", "Buy groceries", "Write report")
        val match = parser.fuzzyMatch(tasks, "buy milk") { it }
        assertEquals("Buy milk", match)
    }

    @Test
    fun `fuzzy match handles substring`() {
        val tasks = listOf("Write the quarterly report", "Call mom")
        val match = parser.fuzzyMatch(tasks, "report") { it }
        assertEquals("Write the quarterly report", match)
    }

    @Test
    fun `fuzzy match tolerates single typo`() {
        val tasks = listOf("Write report", "Buy milk")
        val match = parser.fuzzyMatch(tasks, "writ") { it }
        assertNotNull(match)
    }

    @Test
    fun `fuzzy match returns null when nothing matches`() {
        val tasks = listOf("Buy milk", "Call mom")
        val match = parser.fuzzyMatch(tasks, "xyz unrelated stuff") { it }
        assertNull(match)
    }

    @Test
    fun `fuzzy match with empty list`() {
        assertNull(parser.fuzzyMatch(emptyList<String>(), "anything") { it })
    }
}
