package com.averycorp.prismtask.domain.model.notifications

/**
 * A fully-resolved, immutable snapshot of every notification-delivery
 * decision for a given fire event.
 *
 * The reason this exists as a domain model (separate from the entity) is
 * that resolution involves *inheritance*: the effective delivery for a
 * single reminder is composed from
 *
 *    task override > category default > profile default > global default
 *
 * so the notification helper only needs to consult a [NotificationProfile]
 * and not re-run the resolver each step.
 */
data class NotificationProfile(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val reminderOffsetsMs: List<Long>,
    val urgencyTier: UrgencyTier,
    val soundId: String,
    val soundVolumePercent: Int,
    val soundFadeInMs: Int,
    val soundFadeOutMs: Int,
    val silent: Boolean,
    val vibrationPreset: VibrationPreset,
    val vibrationIntensity: VibrationIntensity,
    val vibrationRepeatCount: Int,
    val vibrationContinuous: Boolean,
    val customVibrationPatternCsv: String?,
    val displayMode: NotificationDisplayMode,
    val lockScreenVisibility: LockScreenVisibility,
    val accentColorHex: String?,
    val badgeMode: BadgeMode,
    val toastPosition: ToastPosition,
    val escalation: EscalationChain,
    val quietHours: QuietHoursWindow,
    val snoozeDurationsMinutes: List<Int>,
    val reAlertIntervalMinutes: Int,
    val reAlertMaxAttempts: Int,
    val watchSyncMode: WatchSyncMode,
    val watchHapticPresetKey: String
) {
    companion object {
        /** Factory for the global default profile used when the DB is empty. */
        fun builtInDefault(id: Long = -1L, name: String = "Default"): NotificationProfile =
            NotificationProfile(
                id = id,
                name = name,
                isBuiltIn = true,
                reminderOffsetsMs = listOf(15 * 60 * 1000L, 0L),
                urgencyTier = UrgencyTier.MEDIUM,
                soundId = BuiltInSound.SYSTEM_DEFAULT_ID,
                soundVolumePercent = 70,
                soundFadeInMs = 0,
                soundFadeOutMs = 0,
                silent = false,
                vibrationPreset = VibrationPreset.SINGLE_PULSE,
                vibrationIntensity = VibrationIntensity.MEDIUM,
                vibrationRepeatCount = 1,
                vibrationContinuous = false,
                customVibrationPatternCsv = null,
                displayMode = NotificationDisplayMode.STANDARD_BANNER,
                lockScreenVisibility = LockScreenVisibility.APP_NAME_ONLY,
                accentColorHex = null,
                badgeMode = BadgeMode.TOTAL_UNREAD,
                toastPosition = ToastPosition.TOP_RIGHT,
                escalation = EscalationChain.DISABLED,
                quietHours = QuietHoursWindow.DISABLED,
                snoozeDurationsMinutes = listOf(5, 15, 30, 60),
                reAlertIntervalMinutes = 5,
                reAlertMaxAttempts = 3,
                watchSyncMode = WatchSyncMode.MIRROR_PHONE,
                watchHapticPresetKey = VibrationPreset.SINGLE_PULSE.key
            )
    }
}
