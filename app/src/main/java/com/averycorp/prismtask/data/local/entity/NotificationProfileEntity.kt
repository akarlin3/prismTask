package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.averycorp.prismtask.domain.model.notifications.BadgeMode
import com.averycorp.prismtask.domain.model.notifications.BuiltInSound
import com.averycorp.prismtask.domain.model.notifications.LockScreenVisibility
import com.averycorp.prismtask.domain.model.notifications.NotificationDisplayMode
import com.averycorp.prismtask.domain.model.notifications.NotificationProfile
import com.averycorp.prismtask.domain.model.notifications.ToastPosition
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import com.averycorp.prismtask.domain.model.notifications.VibrationIntensity
import com.averycorp.prismtask.domain.model.notifications.VibrationPreset
import com.averycorp.prismtask.domain.model.notifications.WatchSyncMode

/**
 * A named notification profile: a full bundle of delivery preferences
 * that users can switch between (Work, Focus, Weekend, Sleep, Travel…).
 *
 * This is the renamed, greatly-expanded successor of
 * `reminder_profiles` (which held only reminder-offset CSV + escalation
 * flag). Migration 39 → 40 renames the table and ADDs new columns with
 * sensible defaults, so existing rows continue to work.
 *
 * Serialized-as-strings fields (escalation JSON, quiet-hours JSON, snooze
 * durations CSV, custom vibration CSV) keep the schema single-row and
 * avoid join fan-out; the domain-model [NotificationProfile] is what
 * callers actually work with.
 */
@Entity(tableName = "reminder_profiles")
data class NotificationProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,

    /** Comma-separated list of offset millis (legacy field, preserved). */
    @ColumnInfo(name = "offsets_csv")
    val offsetsCsv: String,

    /** Legacy boolean: true if escalation should repeat on the simple interval. */
    @ColumnInfo(name = "escalation", defaultValue = "0")
    val escalation: Boolean = false,

    @ColumnInfo(name = "escalation_interval_minutes")
    val escalationIntervalMinutes: Int? = null,

    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // --- v1.4.0 additions (migration 39 → 40) ---

    @ColumnInfo(name = "urgency_tier", defaultValue = "'medium'")
    val urgencyTierKey: String = UrgencyTier.MEDIUM.key,

    @ColumnInfo(name = "sound_id", defaultValue = "'${BuiltInSound.SYSTEM_DEFAULT_ID}'")
    val soundId: String = BuiltInSound.SYSTEM_DEFAULT_ID,

    @ColumnInfo(name = "sound_volume_percent", defaultValue = "70")
    val soundVolumePercent: Int = 70,

    @ColumnInfo(name = "sound_fade_in_ms", defaultValue = "0")
    val soundFadeInMs: Int = 0,

    @ColumnInfo(name = "sound_fade_out_ms", defaultValue = "0")
    val soundFadeOutMs: Int = 0,

    @ColumnInfo(name = "silent", defaultValue = "0")
    val silent: Boolean = false,

    @ColumnInfo(name = "vibration_preset", defaultValue = "'single'")
    val vibrationPresetKey: String = VibrationPreset.SINGLE_PULSE.key,

    @ColumnInfo(name = "vibration_intensity", defaultValue = "'medium'")
    val vibrationIntensityKey: String = VibrationIntensity.MEDIUM.key,

    @ColumnInfo(name = "vibration_repeat_count", defaultValue = "1")
    val vibrationRepeatCount: Int = 1,

    @ColumnInfo(name = "vibration_continuous", defaultValue = "0")
    val vibrationContinuous: Boolean = false,

    /** CSV long[] pattern for [VibrationPreset.CUSTOM]. */
    @ColumnInfo(name = "custom_vibration_pattern_csv")
    val customVibrationPatternCsv: String? = null,

    @ColumnInfo(name = "display_mode", defaultValue = "'standard'")
    val displayModeKey: String = NotificationDisplayMode.STANDARD_BANNER.key,

    @ColumnInfo(name = "lock_screen_visibility", defaultValue = "'app_name'")
    val lockScreenVisibilityKey: String = LockScreenVisibility.APP_NAME_ONLY.key,

    @ColumnInfo(name = "accent_color_hex")
    val accentColorHex: String? = null,

    @ColumnInfo(name = "badge_mode", defaultValue = "'total'")
    val badgeModeKey: String = BadgeMode.TOTAL_UNREAD.key,

    @ColumnInfo(name = "toast_position", defaultValue = "'top_right'")
    val toastPositionKey: String = ToastPosition.TOP_RIGHT.key,

    /** JSON-encoded [com.averycorp.prismtask.domain.model.notifications.EscalationChain]. */
    @ColumnInfo(name = "escalation_chain_json")
    val escalationChainJson: String? = null,

    /** JSON-encoded [com.averycorp.prismtask.domain.model.notifications.QuietHoursWindow]. */
    @ColumnInfo(name = "quiet_hours_json")
    val quietHoursJson: String? = null,

    /** CSV of snooze options in minutes. */
    @ColumnInfo(name = "snooze_durations_csv", defaultValue = "'5,15,30,60'")
    val snoozeDurationsCsv: String = "5,15,30,60",

    @ColumnInfo(name = "re_alert_interval_minutes", defaultValue = "5")
    val reAlertIntervalMinutes: Int = 5,

    @ColumnInfo(name = "re_alert_max_attempts", defaultValue = "3")
    val reAlertMaxAttempts: Int = 3,

    @ColumnInfo(name = "watch_sync_mode", defaultValue = "'mirror'")
    val watchSyncModeKey: String = WatchSyncMode.MIRROR_PHONE.key,

    @ColumnInfo(name = "watch_haptic_preset_key", defaultValue = "'single'")
    val watchHapticPresetKey: String = VibrationPreset.SINGLE_PULSE.key,

    /** Auto-switch rules JSON (list of trigger + predicate). Null = manual only. */
    @ColumnInfo(name = "auto_switch_rules_json")
    val autoSwitchRulesJson: String? = null
) {
    /** Decodes [offsetsCsv] into a list of Long millis. */
    fun offsets(): List<Long> =
        offsetsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }

    /** Decodes [snoozeDurationsCsv] into a list of minute offsets. */
    fun snoozeDurations(): List<Int> =
        snoozeDurationsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }

    companion object {
        fun encodeOffsets(offsets: List<Long>): String = offsets.joinToString(",")
        fun encodeSnoozeDurations(minutes: List<Int>): String = minutes.joinToString(",")
    }
}
