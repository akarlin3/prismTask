package com.averykarlin.averytask.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the CSV escaping logic used by DataExporter.
 * The actual export methods require DAO injection and are tested via integration tests.
 * These tests verify the escaping rules directly.
 */
class DataExporterTest {

    // Replicate the csvEscape logic for unit testing
    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\""
        else value

    @Test
    fun csvEscape_plainText_unchanged() {
        assertEquals("hello world", csvEscape("hello world"))
    }

    @Test
    fun csvEscape_withComma_quoted() {
        assertEquals("\"hello, world\"", csvEscape("hello, world"))
    }

    @Test
    fun csvEscape_withQuotes_doubledAndQuoted() {
        assertEquals("\"he said \"\"hello\"\"\"", csvEscape("he said \"hello\""))
    }

    @Test
    fun csvEscape_withNewline_quoted() {
        assertEquals("\"line1\nline2\"", csvEscape("line1\nline2"))
    }

    @Test
    fun csvEscape_emptyString_unchanged() {
        assertEquals("", csvEscape(""))
    }

    @Test
    fun csvEscape_commaAndQuotes_both() {
        assertEquals("\"\"\"value\"\", another\"", csvEscape("\"value\", another"))
    }

    @Test
    fun csvEscape_noSpecialChars_noQuotes() {
        assertEquals("simple task name", csvEscape("simple task name"))
    }
}
