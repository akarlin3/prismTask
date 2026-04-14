package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.MedicationRefillEntity
import com.averycorp.prismtask.data.local.entity.MoodEnergyLogEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.LifeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalReportGeneratorTest {
    private val gen = ClinicalReportGenerator()
    private val start = 1_776_000_000_000L
    private val end = start + 30L * 24 * 60 * 60 * 1000

    @Test
    fun `empty inputs produce report with no-data sections`() {
        val report = gen.generate(
            ClinicalReportInputs(
                userName = null,
                dateRangeStart = start,
                dateRangeEnd = end
            )
        )
        // All six sections should be present.
        assertEquals(6, report.sections.size)
        val medication = report.sections.first { it.id == ClinicalReportSection.MEDICATION }
        assertTrue(medication.lines.first().contains("No data"))
    }

    @Test
    fun `mood section averages entries`() {
        val inputs = ClinicalReportInputs(
            userName = "Test",
            dateRangeStart = start,
            dateRangeEnd = end,
            moodEnergyLogs = listOf(
                MoodEnergyLogEntity(date = start, mood = 4, energy = 4),
                MoodEnergyLogEntity(date = start + 1, mood = 2, energy = 3),
                MoodEnergyLogEntity(date = start + 2, mood = 5, energy = 5)
            )
        )
        val report = gen.generate(inputs)
        val mood = report.sections.first { it.id == ClinicalReportSection.MOOD_ENERGY }
        // Avg mood = (4+2+5)/3 ≈ 3.7
        assertTrue(mood.lines.any { it.contains("3.7") })
        // 1 low-mood day (mood=2)
        assertTrue(mood.lines.any { it.contains("Low-mood days (≤2): 1") })
    }

    @Test
    fun `task section counts completed within the range`() {
        val inputs = ClinicalReportInputs(
            userName = "Test",
            dateRangeStart = start,
            dateRangeEnd = end,
            tasks = listOf(
                TaskEntity(title = "a", isCompleted = true, completedAt = start + 1_000_000),
                TaskEntity(title = "b", isCompleted = true, completedAt = start - 1000),
                TaskEntity(title = "c", isCompleted = false, dueDate = start + 100)
            )
        )
        val report = gen.generate(inputs)
        val tasks = report.sections.first { it.id == ClinicalReportSection.TASKS }
        assertTrue(tasks.lines.any { it.contains("Completed: 1") })
        assertTrue(tasks.lines.any { it.contains("Still open: 1") })
    }

    @Test
    fun `balance section uses life category`() {
        val inputs = ClinicalReportInputs(
            userName = null,
            dateRangeStart = start,
            dateRangeEnd = end,
            tasks = listOf(
                TaskEntity(
                    title = "work a",
                    isCompleted = true,
                    completedAt = start + 100,
                    lifeCategory = LifeCategory.WORK.name
                ),
                TaskEntity(
                    title = "self-care b",
                    isCompleted = true,
                    completedAt = start + 200,
                    lifeCategory = LifeCategory.SELF_CARE.name
                )
            )
        )
        val report = gen.generate(inputs)
        val balance = report.sections.first { it.id == ClinicalReportSection.BALANCE }
        assertTrue(balance.lines.any { it.contains("Work: 1") })
        assertTrue(balance.lines.any { it.contains("Self-Care: 1") })
    }

    @Test
    fun `medication section formats pill count and adherence`() {
        val inputs = ClinicalReportInputs(
            userName = null,
            dateRangeStart = start,
            dateRangeEnd = end,
            medications = listOf(
                MedicationRefillEntity(medicationName = "Wellbutrin", pillCount = 12)
            ),
            medicationAdherencePercentages = mapOf("Wellbutrin" to 0.92f)
        )
        val report = gen.generate(inputs)
        val med = report.sections.first { it.id == ClinicalReportSection.MEDICATION }
        assertTrue(med.lines.first().contains("Wellbutrin"))
        assertTrue(med.lines.first().contains("12 pills"))
        assertTrue(med.lines.first().contains("92%"))
    }

    @Test
    fun `burnout section emits avg peak and trough`() {
        val inputs = ClinicalReportInputs(
            userName = null,
            dateRangeStart = start,
            dateRangeEnd = end,
            burnoutScoresByDay = mapOf(
                start to 40,
                start + 1 to 60,
                start + 2 to 30
            )
        )
        val report = gen.generate(inputs)
        val burnout = report.sections.first { it.id == ClinicalReportSection.BURNOUT }
        // avg = (40+60+30)/3 = 43
        assertTrue(burnout.lines.any { it.contains("Average score: 43") })
        assertTrue(burnout.lines.any { it.contains("Peak score: 60") })
        assertTrue(burnout.lines.any { it.contains("Lowest score: 30") })
    }

    @Test
    fun `section filter drops excluded sections`() {
        val inputs = ClinicalReportInputs(
            userName = null,
            dateRangeStart = start,
            dateRangeEnd = end,
            sectionsEnabled = setOf(ClinicalReportSection.OVERVIEW, ClinicalReportSection.TASKS)
        )
        val report = gen.generate(inputs)
        assertEquals(2, report.sections.size)
        assertTrue(report.sections.any { it.id == ClinicalReportSection.OVERVIEW })
        assertTrue(report.sections.any { it.id == ClinicalReportSection.TASKS })
    }

    @Test
    fun `plain text contains title subtitle and footer`() {
        val report = gen.generate(
            ClinicalReportInputs(
                userName = "Alice",
                dateRangeStart = start,
                dateRangeEnd = end
            )
        )
        assertTrue(report.plainText.contains("PrismTask Health & Wellness Report"))
        assertTrue(report.plainText.contains("For Alice"))
        assertTrue(report.plainText.contains("Not a medical document"))
    }
}
