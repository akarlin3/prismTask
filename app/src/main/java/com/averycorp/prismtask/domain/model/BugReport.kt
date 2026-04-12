package com.averycorp.prismtask.domain.model

enum class BugCategory {
    CRASH, UI_GLITCH, FEATURE_NOT_WORKING, DATA_LOSS,
    PERFORMANCE, SYNC_ISSUE, WIDGET_ISSUE, FEATURE_REQUEST, OTHER
}

enum class BugSeverity { MINOR, MAJOR, CRITICAL }

enum class ReportStatus { SUBMITTED, ACKNOWLEDGED, FIXED, WONT_FIX }

data class BugReport(
    val id: String,
    val userId: String?,
    val category: BugCategory,
    val description: String,
    val severity: BugSeverity,
    val steps: List<String>,
    val screenshotUris: List<String>,
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: Int,
    val appVersion: String,
    val appVersionCode: Int,
    val buildType: String,
    val userTier: String,
    val currentScreen: String,
    val taskCount: Int,
    val habitCount: Int,
    val availableRamMb: Int,
    val freeStorageMb: Int,
    val networkType: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val timestamp: Long,
    val status: ReportStatus,
    val diagnosticLog: String? = null,
    val submittedVia: String = "firestore"
)
