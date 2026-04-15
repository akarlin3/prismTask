package com.averycorp.prismtask.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomSoundEntityTest {
    @Test
    fun `soundId formats with custom_ prefix`() {
        val entity = buildEntity(id = 42)
        assertEquals("custom_42", entity.soundId())
    }

    @Test
    fun `parseId extracts numeric id when prefixed`() {
        assertEquals(42L, CustomSoundEntity.parseId("custom_42"))
    }

    @Test
    fun `parseId returns null for non-custom ids`() {
        assertNull(CustomSoundEntity.parseId("chime_gentle"))
        assertNull(CustomSoundEntity.parseId("__system_default__"))
    }

    @Test
    fun `parseId returns null for custom_ without a number`() {
        assertNull(CustomSoundEntity.parseId("custom_abc"))
    }

    @Test
    fun `supported formats include the documented set`() {
        assertEquals(setOf("mp3", "wav", "m4a", "ogg"), CustomSoundEntity.SUPPORTED_FORMATS)
    }

    @Test
    fun `size and duration limits match spec`() {
        assertEquals(10L * 1024L * 1024L, CustomSoundEntity.MAX_SIZE_BYTES)
        assertEquals(30_000L, CustomSoundEntity.MAX_DURATION_MS)
    }

    private fun buildEntity(id: Long = 1L): CustomSoundEntity = CustomSoundEntity(
        id = id,
        name = "Test",
        originalFilename = "test.mp3",
        uri = "file:///tmp/test.mp3",
        format = "mp3",
        sizeBytes = 1024L,
        durationMs = 1500L,
        createdAt = 0L
    )
}
