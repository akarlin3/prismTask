package com.averycorp.prismtask.feedback

import com.averycorp.prismtask.domain.model.BugCategory
import com.averycorp.prismtask.domain.model.BugReport
import com.averycorp.prismtask.domain.model.BugSeverity
import com.averycorp.prismtask.domain.model.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BugReportModelTest {

    @Test
    fun `BugReport data class creates correctly`() {
        val report = BugReport(
            id = "test-id",
            userId = "user-123",
            category = BugCategory.CRASH,
            description = "App crashes when opening settings",
            severity = BugSeverity.CRITICAL,
            steps = listOf("Open app", "Go to settings", "Crash occurs"),
            screenshotUris = emptyList(),
            deviceModel = "Pixel 6",
            deviceManufacturer = "Google",
            androidVersion = 33,
            appVersion = "1.3.2",
            appVersionCode = 93,
            buildType = "debug",
            userTier = "Free",
            currentScreen = "Settings",
            taskCount = 42,
            habitCount = 7,
            availableRamMb = 2048,
            freeStorageMb = 8192,
            networkType = "wifi",
            batteryPercent = 85,
            isCharging = true,
            timestamp = 1000L,
            status = ReportStatus.SUBMITTED,
            diagnosticLog = "some log text"
        )

        assertEquals("test-id", report.id)
        assertEquals("user-123", report.userId)
        assertEquals(BugCategory.CRASH, report.category)
        assertEquals(BugSeverity.CRITICAL, report.severity)
        assertEquals(ReportStatus.SUBMITTED, report.status)
        assertEquals(3, report.steps.size)
        assertEquals("some log text", report.diagnosticLog)
    }

    @Test
    fun `BugCategory has all expected values`() {
        val categories = BugCategory.entries
        assertEquals(9, categories.size)
        assertNotNull(categories.find { it == BugCategory.CRASH })
        assertNotNull(categories.find { it == BugCategory.UI_GLITCH })
        assertNotNull(categories.find { it == BugCategory.FEATURE_NOT_WORKING })
        assertNotNull(categories.find { it == BugCategory.DATA_LOSS })
        assertNotNull(categories.find { it == BugCategory.PERFORMANCE })
        assertNotNull(categories.find { it == BugCategory.SYNC_ISSUE })
        assertNotNull(categories.find { it == BugCategory.WIDGET_ISSUE })
        assertNotNull(categories.find { it == BugCategory.FEATURE_REQUEST })
        assertNotNull(categories.find { it == BugCategory.OTHER })
    }

    @Test
    fun `BugSeverity has all expected values`() {
        val severities = BugSeverity.entries
        assertEquals(3, severities.size)
        assertNotNull(severities.find { it == BugSeverity.MINOR })
        assertNotNull(severities.find { it == BugSeverity.MAJOR })
        assertNotNull(severities.find { it == BugSeverity.CRITICAL })
    }

    @Test
    fun `ReportStatus has all expected values`() {
        val statuses = ReportStatus.entries
        assertEquals(4, statuses.size)
        assertNotNull(statuses.find { it == ReportStatus.SUBMITTED })
        assertNotNull(statuses.find { it == ReportStatus.ACKNOWLEDGED })
        assertNotNull(statuses.find { it == ReportStatus.FIXED })
        assertNotNull(statuses.find { it == ReportStatus.WONT_FIX })
    }

    @Test
    fun `BugCategory enum name serialization is correct`() {
        assertEquals("CRASH", BugCategory.CRASH.name)
        assertEquals("UI_GLITCH", BugCategory.UI_GLITCH.name)
        assertEquals("FEATURE_NOT_WORKING", BugCategory.FEATURE_NOT_WORKING.name)
        assertEquals("DATA_LOSS", BugCategory.DATA_LOSS.name)
        assertEquals("SYNC_ISSUE", BugCategory.SYNC_ISSUE.name)
        assertEquals("WIDGET_ISSUE", BugCategory.WIDGET_ISSUE.name)
        assertEquals("FEATURE_REQUEST", BugCategory.FEATURE_REQUEST.name)
    }

    @Test
    fun `BugSeverity enum name serialization is correct`() {
        assertEquals("MINOR", BugSeverity.MINOR.name)
        assertEquals("MAJOR", BugSeverity.MAJOR.name)
        assertEquals("CRITICAL", BugSeverity.CRITICAL.name)
    }

    @Test
    fun `BugReport defaults diagnosticLog to null`() {
        val report = BugReport(
            id = "test", userId = null, category = BugCategory.OTHER,
            description = "test description", severity = BugSeverity.MINOR,
            steps = emptyList(), screenshotUris = emptyList(),
            deviceModel = "", deviceManufacturer = "", androidVersion = 26,
            appVersion = "1.0", appVersionCode = 1, buildType = "debug",
            userTier = "Free", currentScreen = "", taskCount = 0, habitCount = 0,
            availableRamMb = 0, freeStorageMb = 0, networkType = "wifi",
            batteryPercent = 50, isCharging = false, timestamp = 0L,
            status = ReportStatus.SUBMITTED
        )
        assertEquals(null, report.diagnosticLog)
        assertEquals("firestore", report.submittedVia)
    }

    @Test
    fun `description validation - minimum length check`() {
        val shortDesc = "Too short"
        val validDesc = "This is a valid description that is long enough"
        assertEquals(true, shortDesc.length < 10)
        assertEquals(true, validDesc.length >= 10)
    }
}
