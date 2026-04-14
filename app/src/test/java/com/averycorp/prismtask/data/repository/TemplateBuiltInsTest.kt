package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.entity.ProjectTemplateEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the shape of the v1.3.0 P15 built-in project + habit templates.
 * Uses the BUILT_INS companion lists directly so these tests don't need a
 * database or DI.
 */
class TemplateBuiltInsTest {
    private val gson = Gson()

    @Test
    fun `project templates has three built ins`() {
        assertEquals(3, ProjectTemplateRepository.BUILT_INS.size)
        val names = ProjectTemplateRepository.BUILT_INS.map { it.name }
        assertTrue("Sprint" in names)
        assertTrue("Event Planning" in names)
        assertTrue("Course" in names)
    }

    @Test
    fun `sprint template has four tasks`() {
        val sprint = ProjectTemplateRepository.BUILT_INS.first { it.name == "Sprint" }
        assertEquals(4, sprint.tasks.size)
    }

    @Test
    fun `event planning template has six tasks`() {
        val evt = ProjectTemplateRepository.BUILT_INS.first { it.name == "Event Planning" }
        assertEquals(6, evt.tasks.size)
    }

    @Test
    fun `course template has five tasks`() {
        val course = ProjectTemplateRepository.BUILT_INS.first { it.name == "Course" }
        assertEquals(5, course.tasks.size)
    }

    @Test
    fun `inline task list round trips through gson`() {
        val list = ProjectTemplateRepository.BUILT_INS.first().tasks
        val json = gson.toJson(list)
        val type = TypeToken.getParameterized(List::class.java, ProjectTemplateRepository.InlineTask::class.java).type
        val parsed: List<ProjectTemplateRepository.InlineTask> = gson.fromJson(json, type)
        assertEquals(list, parsed)
    }

    @Test
    fun `habit templates has four built ins`() {
        assertEquals(4, HabitTemplateRepository.BUILT_INS.size)
        val names = HabitTemplateRepository.BUILT_INS.map { it.name }
        assertTrue("Exercise" in names)
        assertTrue("Reading" in names)
        assertTrue("Meditation" in names)
        assertTrue("Weekly Review" in names)
    }

    @Test
    fun `weekly review template is weekly frequency`() {
        val review = HabitTemplateRepository.BUILT_INS.first { it.name == "Weekly Review" }
        assertEquals("WEEKLY", review.frequency)
        assertEquals("7", review.activeDaysCsv)
    }

    @Test
    fun `daily habit templates have empty active days`() {
        val daily = HabitTemplateRepository.BUILT_INS.filter { it.frequency == "DAILY" }
        assertTrue(daily.isNotEmpty())
        daily.forEach { assertEquals("", it.activeDaysCsv) }
    }

    @Test
    fun `project template entity defaults usage count to zero`() {
        val e = ProjectTemplateEntity(
            name = "Test",
            taskTemplatesJson = "[]",
            isBuiltIn = false
        )
        assertEquals(0, e.usageCount)
        assertEquals(null, e.lastUsedAt)
    }
}
