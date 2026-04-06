package com.averykarlin.averytask.domain

import com.averykarlin.averytask.domain.usecase.extractKeywords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    // Tests for the extractKeywords top-level function

    @Test
    fun extractKeywords_basic() {
        val keywords = extractKeywords("Buy groceries today")
        assertTrue(keywords.contains("buy"))
        assertTrue(keywords.contains("groceries"))
        assertTrue(keywords.contains("today"))
    }

    @Test
    fun extractKeywords_filtersStopWords() {
        val keywords = extractKeywords("Go to the store for milk")
        assertFalse(keywords.contains("the"))
        assertFalse(keywords.contains("to"))
        assertFalse(keywords.contains("for"))
        assertFalse(keywords.contains("go"))
    }

    @Test
    fun extractKeywords_filtersShortWords() {
        val keywords = extractKeywords("Do it by 5pm ok")
        assertFalse(keywords.contains("it"))
        assertFalse(keywords.contains("by"))
        assertFalse(keywords.contains("ok"))
    }

    @Test
    fun extractKeywords_lowercases() {
        val keywords = extractKeywords("Review PULL Request")
        assertTrue(keywords.contains("review"))
        assertTrue(keywords.contains("pull"))
        assertTrue(keywords.contains("request"))
    }

    @Test
    fun extractKeywords_emptyInput() {
        assertEquals(emptyList<String>(), extractKeywords(""))
    }

    @Test
    fun extractKeywords_onlyStopWords() {
        val keywords = extractKeywords("the a an is at to")
        assertTrue(keywords.isEmpty())
    }

    @Test
    fun extractKeywords_multipleSpaces() {
        val keywords = extractKeywords("meeting   about   team")
        assertTrue(keywords.contains("meeting"))
        assertTrue(keywords.contains("about"))
        assertTrue(keywords.contains("team"))
    }

    @Test
    fun extractKeywords_singleLongWord() {
        val keywords = extractKeywords("refactoring")
        assertEquals(listOf("refactoring"), keywords)
    }
}
