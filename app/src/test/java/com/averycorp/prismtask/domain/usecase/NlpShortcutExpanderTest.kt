package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.repository.NlpShortcutRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NlpShortcutExpanderTest {

    private val hw = NlpShortcutExpander.Shortcut("hw", "Homework assignment @School !medium")
    private val mtg = NlpShortcutExpander.Shortcut("mtg", "Meeting #work !high")
    private val bug = NlpShortcutExpander.Shortcut("bug", "Fix bug #dev !urgent")

    @Test
    fun `empty input returns empty`() {
        assertEquals("", NlpShortcutExpander.expand("", listOf(hw)))
    }

    @Test
    fun `empty shortcuts list returns input unchanged`() {
        assertEquals("show me", NlpShortcutExpander.expand("show me", emptyList()))
    }

    @Test
    fun `trigger at start of input expands`() {
        val result = NlpShortcutExpander.expand("hw math chapter 5", listOf(hw))
        assertEquals("Homework assignment @School !medium math chapter 5", result)
    }

    @Test
    fun `trigger in middle of input expands with word boundaries`() {
        val result = NlpShortcutExpander.expand("urgent hw today", listOf(hw))
        assertEquals("urgent Homework assignment @School !medium today", result)
    }

    @Test
    fun `trigger at end of input expands`() {
        val result = NlpShortcutExpander.expand("finish hw", listOf(hw))
        assertEquals("finish Homework assignment @School !medium", result)
    }

    @Test
    fun `substring of word is not expanded`() {
        // "show" should not trigger "hw"
        val result = NlpShortcutExpander.expand("show me the door", listOf(hw))
        assertEquals("show me the door", result)
    }

    @Test
    fun `trigger as part of larger word is not expanded`() {
        val result = NlpShortcutExpander.expand("hwkcnt", listOf(hw))
        assertEquals("hwkcnt", result)
    }

    @Test
    fun `multiple triggers in one input all expand`() {
        val result = NlpShortcutExpander.expand("hw and mtg", listOf(hw, mtg))
        assertEquals(
            "Homework assignment @School !medium and Meeting #work !high",
            result
        )
    }

    @Test
    fun `longer matching trigger wins over shorter prefix`() {
        val hwk = NlpShortcutExpander.Shortcut("hwk", "Homework")
        val h = NlpShortcutExpander.Shortcut("h", "H")
        val result = NlpShortcutExpander.expand("hwk done", listOf(h, hwk))
        assertEquals("Homework done", result)
    }

    @Test
    fun `punctuation acts as word boundary`() {
        val result = NlpShortcutExpander.expand("finish hw.", listOf(hw))
        assertEquals("finish Homework assignment @School !medium.", result)
    }

    @Test
    fun `trigger validator accepts 2 to 10 alphanumeric`() {
        assertTrue(NlpShortcutRepository.isValidTrigger("hw"))
        assertTrue(NlpShortcutRepository.isValidTrigger("mtg"))
        assertTrue(NlpShortcutRepository.isValidTrigger("task123"))
        assertTrue(NlpShortcutRepository.isValidTrigger("a1b2c3d4e5"))
    }

    @Test
    fun `trigger validator rejects too short too long and non alnum`() {
        assertFalse(NlpShortcutRepository.isValidTrigger("a"))
        assertFalse(NlpShortcutRepository.isValidTrigger("abcdefghijk"))
        assertFalse(NlpShortcutRepository.isValidTrigger("has space"))
        assertFalse(NlpShortcutRepository.isValidTrigger("has-dash"))
        assertFalse(NlpShortcutRepository.isValidTrigger(""))
    }

    @Test
    fun `built in shortcuts are four distinct triggers`() {
        val triggers = NlpShortcutRepository.BUILT_IN_SHORTCUTS.map { it.first }
        assertEquals(4, triggers.size)
        assertEquals(4, triggers.toSet().size)
    }
}
