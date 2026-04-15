package com.averycorp.prismtask.domain.model.notifications

/**
 * Domain enums for the customizable notification system.
 *
 * These are the vocabulary shared between preferences, profile entities,
 * settings UI, and the [com.averycorp.prismtask.notifications.NotificationHelper]
 * so users' choices survive a round-trip through Room, DataStore, and the
 * platform notification APIs without string fragility.
 *
 * Each enum exposes a stable wire-format [key] distinct from [name]; [name]
 * is free to be refactored while [key] is the persistence contract.
 */

/** Priority / urgency tier assigned to an individual fired notification. */
enum class UrgencyTier(val key: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    CRITICAL("critical", "Critical");

    companion object {
        val DEFAULT = MEDIUM

        fun fromKey(key: String?): UrgencyTier = values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** How a notification is visually presented when it fires. */
enum class NotificationDisplayMode(val key: String, val label: String) {
    STANDARD_BANNER("standard", "Standard banner"),
    PERSISTENT_BANNER("persistent", "Persistent banner"),
    FULL_SCREEN("full_screen", "Full-screen takeover"),
    MINIMAL_CORNER("minimal", "Minimal corner toast");

    companion object {
        val DEFAULT = STANDARD_BANNER

        fun fromKey(key: String?): NotificationDisplayMode =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Lock-screen privacy for notifications from this profile/category. */
enum class LockScreenVisibility(val key: String, val label: String) {
    SHOW_ALL("show_all", "Show content"),
    APP_NAME_ONLY("app_name", "Show app name only"),
    HIDDEN("hidden", "Hide from lock screen");

    companion object {
        val DEFAULT = APP_NAME_ONLY

        fun fromKey(key: String?): LockScreenVisibility =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Categorization of each notification surface users can customize independently. */
enum class NotificationCategory(val key: String, val label: String) {
    TASK_REMINDER("task", "Task reminders"),
    HABIT_REMINDER("habit", "Habit reminders"),
    MEDICATION("medication", "Medication"),
    TIMER("timer", "Timer"),
    DAILY_BRIEFING("briefing", "Daily briefing"),
    EVENING_SUMMARY("evening_summary", "Evening summary"),
    WEEKLY_SUMMARY("weekly_summary", "Weekly summary"),
    REENGAGEMENT("reengagement", "Re-engagement"),
    BALANCE_ALERT("balance", "Balance alerts"),
    COLLABORATOR("collaborator", "Collaborator updates"),
    STREAK("streak", "Streaks & gamification");

    companion object {
        fun fromKey(key: String?): NotificationCategory? =
            values().firstOrNull { it.key == key }
    }
}

/** Built-in sound categories exposed in the sound picker. */
enum class SoundCategory(val key: String, val label: String) {
    CHIMES("chimes", "Chimes"),
    BELLS("bells", "Bells"),
    NATURE("nature", "Nature"),
    SCI_FI("sci_fi", "Sci-Fi"),
    MINIMAL("minimal", "Minimal"),
    PERCUSSIVE("percussive", "Percussive"),
    VOICE("voice", "Voice"),
    CUSTOM("custom", "My uploads");

    companion object {
        fun fromKey(key: String?): SoundCategory? =
            values().firstOrNull { it.key == key }
    }
}

/** Built-in vibration presets. [CUSTOM] sources its pattern from a user-recorded [android.os.VibrationEffect]-compatible long[]. */
enum class VibrationPreset(val key: String, val label: String) {
    NONE("none", "No vibration"),
    SINGLE_PULSE("single", "Single pulse"),
    DOUBLE_PULSE("double", "Double pulse"),
    TRIPLE("triple", "Triple"),
    LONG_BUZZ("long", "Long buzz"),
    SOS("sos", "SOS"),
    HEARTBEAT("heartbeat", "Heartbeat"),
    WAVE("wave", "Wave"),
    CUSTOM("custom", "Custom pattern");

    companion object {
        val DEFAULT = SINGLE_PULSE

        fun fromKey(key: String?): VibrationPreset =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Haptic intensity level. Effective on hardware that reports amplitude support. */
enum class VibrationIntensity(val key: String, val label: String, val amplitude: Int) {
    LIGHT("light", "Light", 80),
    MEDIUM("medium", "Medium", 160),
    STRONG("strong", "Strong", 255);

    companion object {
        val DEFAULT = MEDIUM

        fun fromKey(key: String?): VibrationIntensity =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Badge-count strategy on the launcher/dock. */
enum class BadgeMode(val key: String, val label: String) {
    OFF("off", "Off"),
    TOTAL_UNREAD("total", "Total unread"),
    PER_CATEGORY("per_category", "Per category"),
    PRIORITY_ONLY("priority", "High/critical only");

    companion object {
        val DEFAULT = TOTAL_UNREAD

        fun fromKey(key: String?): BadgeMode =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** On-screen position for web/desktop toasts — mobile ignores this. */
enum class ToastPosition(val key: String, val label: String) {
    TOP_RIGHT("top_right", "Top right"),
    TOP_CENTER("top_center", "Top center"),
    BOTTOM_RIGHT("bottom_right", "Bottom right"),
    CENTER("center", "Center"),
    FULL_SCREEN("full_screen", "Full screen");

    companion object {
        val DEFAULT = TOP_RIGHT

        fun fromKey(key: String?): ToastPosition =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Single step inside an escalation chain. */
enum class EscalationStepAction(val key: String, val label: String) {
    GENTLE_PING("gentle", "Gentle ping"),
    STANDARD_ALERT("standard", "Standard alert"),
    LOUD_VIBRATE("loud", "Louder + vibrate"),
    FULL_SCREEN("full_screen", "Full-screen takeover");

    companion object {
        fun fromKey(key: String?): EscalationStepAction? =
            values().firstOrNull { it.key == key }
    }
}

/** Tone for the AI-generated daily briefing. */
enum class BriefingTone(val key: String, val label: String) {
    CONCISE("concise", "Concise"),
    CONVERSATIONAL("conversational", "Conversational"),
    MOTIVATIONAL("motivational", "Motivational");

    companion object {
        val DEFAULT = CONCISE

        fun fromKey(key: String?): BriefingTone =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Trigger types that can switch the active notification profile automatically. */
enum class ProfileAutoSwitchTrigger(val key: String, val label: String) {
    TIME_OF_DAY("time", "Time of day"),
    DAY_OF_WEEK("day", "Day of week"),
    LOCATION("location", "Location (mobile)"),
    CALENDAR_EVENT("calendar", "Calendar event"),
    OS_FOCUS_MODE("focus_mode", "OS Focus / Modes");

    companion object {
        fun fromKey(key: String?): ProfileAutoSwitchTrigger? =
            values().firstOrNull { it.key == key }
    }
}

/** How collaborator notifications are delivered. */
enum class CollaboratorDigestMode(val key: String, val label: String) {
    IMMEDIATE("immediate", "Immediately"),
    HOURLY("hourly", "Hourly digest"),
    DAILY("daily", "Daily digest"),
    MUTED("muted", "Muted");

    companion object {
        val DEFAULT = IMMEDIATE

        fun fromKey(key: String?): CollaboratorDigestMode =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** How smartwatch notifications relate to phone settings. */
enum class WatchSyncMode(val key: String, val label: String) {
    MIRROR_PHONE("mirror", "Mirror phone"),
    WATCH_ONLY("watch_only", "Route to watch only"),
    DIFFERENTIATED("differentiated", "Separate watch settings"),
    DISABLED("disabled", "Disable watch alerts");

    companion object {
        val DEFAULT = MIRROR_PHONE

        fun fromKey(key: String?): WatchSyncMode =
            values().firstOrNull { it.key == key } ?: DEFAULT
    }
}
