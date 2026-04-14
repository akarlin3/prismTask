package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.usecase.NlpShortcutExpander
import com.averycorp.prismtask.domain.usecase.NlpShortcutExpander.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [NlpShortcutExpander]. This is a pure object so no fakes
 * or Hilt plumbing are needed.
 */
class NlpShortcutExpanderTest {
    @Test
    fun expand_returnsInputUnchangedWhenNoShortcutsMatch() {
        val input = "Write the weekly report"
        val result = NlpShortcutExpander.expand(
            input,
            listOf(Shortcut("hw", "homework"))
        )
        assertEquals(input, result)
    }

    @Test
    fun expand_replacesWholeWordShortcut() {
        val result = NlpShortcutExpander.expand(
            "hw math chapter 5",
            listOf(Shortcut("hw", "homework"))
        )
        assertEquals("homework math chapter 5", result)
    }

    @Test
    fun expand_doesNotMatchInsideLongerWord() {
        // "show" should NOT trigger "hw" — word-boundary matching required.
        val result = NlpShortcutExpander.expand(
            "show tell",
            listOf(Shortcut("hw", "homework"))
        )
        assertEquals("show tell", result)
    }

    @Test
    fun expand_prefersLongerTriggerWhenMultipleMatch() {
        // Both "hw" and "hwk" start at position 0. The longer one wins.
        val result = NlpShortcutExpander.expand(
            "hwk algebra",
            listOf(
                Shortcut("hw", "homework"),
                Shortcut("hwk", "homework kinetics")
            )
        )
        assertEquals("homework kinetics algebra", result)
    }

    @Test
    fun expand_caseSensitiveByDesign() {
        // Stored trigger is "hw" — "HW" should NOT match.
        val result = NlpShortcutExpander.expand(
            "HW chapter 5",
            listOf(Shortcut("hw", "homework"))
        )
        assertEquals("HW chapter 5", result)
    }

    @Test
    fun expand_withBlankInputReturnsInputImmediately() {
        assertEquals("", NlpShortcutExpander.expand("", listOf(Shortcut("hw", "homework"))))
    }

    @Test
    fun expand_withEmptyShortcutListReturnsInputUnchanged() {
        val input = "some text"
        assertEquals(input, NlpShortcutExpander.expand(input, emptyList()))
    }

    @Test
    fun expand_replacesMultipleOccurrencesInSameString() {
        val result = NlpShortcutExpander.expand(
            "hw now, then more hw later",
            listOf(Shortcut("hw", "homework"))
        )
        assertEquals("homework now, then more homework later", result)
    }
}
