package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.NotificationProfileEntity
import com.averycorp.prismtask.domain.model.notifications.BadgeMode
import com.averycorp.prismtask.domain.model.notifications.BuiltInSound
import com.averycorp.prismtask.domain.model.notifications.EscalationChain
import com.averycorp.prismtask.domain.model.notifications.EscalationStep
import com.averycorp.prismtask.domain.model.notifications.EscalationStepAction
import com.averycorp.prismtask.domain.model.notifications.LockScreenVisibility
import com.averycorp.prismtask.domain.model.notifications.NotificationDisplayMode
import com.averycorp.prismtask.domain.model.notifications.NotificationProfile
import com.averycorp.prismtask.domain.model.notifications.QuietHoursWindow
import com.averycorp.prismtask.domain.model.notifications.ToastPosition
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import com.averycorp.prismtask.domain.model.notifications.VibrationIntensity
import com.averycorp.prismtask.domain.model.notifications.VibrationPreset
import com.averycorp.prismtask.domain.model.notifications.WatchSyncMode
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Hydrates a persisted [NotificationProfileEntity] — where escalation
 * chains and quiet hours live as JSON blobs — into a fully-typed
 * [NotificationProfile] the runtime helpers can consume without ever
 * touching Gson.
 *
 * Kept pure so it's trivially unit-testable. Gson is a single dependency
 * injected once; callers reuse the same instance across profiles.
 */
class NotificationProfileResolver(private val gson: Gson = Gson()) {
    fun resolve(entity: NotificationProfileEntity): NotificationProfile = NotificationProfile(
        id = entity.id,
        name = entity.name,
        isBuiltIn = entity.isBuiltIn,
        reminderOffsetsMs = entity.offsets(),
        urgencyTier = UrgencyTier.fromKey(entity.urgencyTierKey),
        soundId = entity.soundId.ifBlank { BuiltInSound.SYSTEM_DEFAULT_ID },
        soundVolumePercent = entity.soundVolumePercent.coerceIn(0, 100),
        soundFadeInMs = entity.soundFadeInMs.coerceIn(0, 5000),
        soundFadeOutMs = entity.soundFadeOutMs.coerceIn(0, 5000),
        silent = entity.silent,
        vibrationPreset = VibrationPreset.fromKey(entity.vibrationPresetKey),
        vibrationIntensity = VibrationIntensity.fromKey(entity.vibrationIntensityKey),
        vibrationRepeatCount = entity.vibrationRepeatCount.coerceIn(0, 10),
        vibrationContinuous = entity.vibrationContinuous,
        customVibrationPatternCsv = entity.customVibrationPatternCsv,
        displayMode = NotificationDisplayMode.fromKey(entity.displayModeKey),
        lockScreenVisibility = LockScreenVisibility.fromKey(entity.lockScreenVisibilityKey),
        accentColorHex = entity.accentColorHex,
        badgeMode = BadgeMode.fromKey(entity.badgeModeKey),
        toastPosition = ToastPosition.fromKey(entity.toastPositionKey),
        escalation = decodeEscalationChain(
            entity.escalationChainJson,
            legacyFlag = entity.escalation,
            legacyInterval = entity.escalationIntervalMinutes
        ),
        quietHours = decodeQuietHours(entity.quietHoursJson),
        snoozeDurationsMinutes = entity.snoozeDurations().ifEmpty { listOf(5, 15, 30, 60) },
        reAlertIntervalMinutes = entity.reAlertIntervalMinutes.coerceIn(1, 60),
        reAlertMaxAttempts = entity.reAlertMaxAttempts.coerceIn(1, 10),
        watchSyncMode = WatchSyncMode.fromKey(entity.watchSyncModeKey),
        watchHapticPresetKey = entity.watchHapticPresetKey
    )

    fun encodeEscalationChain(chain: EscalationChain): String = gson.toJson(chain.toWire())

    fun encodeQuietHours(window: QuietHoursWindow): String = gson.toJson(window.toWire())

    private fun decodeEscalationChain(
        json: String?,
        legacyFlag: Boolean,
        legacyInterval: Int?
    ): EscalationChain {
        if (!json.isNullOrBlank()) {
            return try {
                gson.fromJson(json, EscalationChainWire::class.java).toDomain()
            } catch (_: JsonSyntaxException) {
                EscalationChain.DISABLED
            }
        }
        // Backfill from legacy boolean: a simple "every N minutes" loop
        // becomes a 1-step chain that the scheduler treats as a repeating
        // re-alert rather than a full chain.
        if (legacyFlag && legacyInterval != null) {
            val delayMs = legacyInterval.toLong() * 60_000L
            return EscalationChain(
                enabled = true,
                steps = listOf(
                    EscalationStep(EscalationStepAction.STANDARD_ALERT, delayMs = delayMs),
                    EscalationStep(EscalationStepAction.LOUD_VIBRATE, delayMs = delayMs),
                    EscalationStep(EscalationStepAction.FULL_SCREEN, delayMs = delayMs)
                ),
                maxAttempts = 4
            )
        }
        return EscalationChain.DISABLED
    }

    private fun decodeQuietHours(json: String?): QuietHoursWindow {
        if (json.isNullOrBlank()) return QuietHoursWindow.DISABLED
        return try {
            gson.fromJson(json, QuietHoursWire::class.java).toDomain()
        } catch (_: JsonSyntaxException) {
            QuietHoursWindow.DISABLED
        }
    }

    // -- Wire types: plain-data shapes suitable for Gson round-trip --

    private data class EscalationChainWire(
        val enabled: Boolean = false,
        val steps: List<EscalationStepWire> = emptyList(),
        val stopOnInteraction: Boolean = true,
        val maxAttempts: Int = 5
    ) {
        fun toDomain(): EscalationChain = EscalationChain(
            enabled = enabled,
            steps = steps.mapNotNull { it.toDomainOrNull() },
            stopOnInteraction = stopOnInteraction,
            maxAttempts = maxAttempts
        )
    }

    private data class EscalationStepWire(
        val action: String = EscalationStepAction.STANDARD_ALERT.key,
        val delayMs: Long = 0L,
        val triggerTiers: List<String> = emptyList()
    ) {
        fun toDomainOrNull(): EscalationStep? {
            val a = EscalationStepAction.fromKey(action) ?: return null
            val tiers = triggerTiers.mapNotNull { UrgencyTier.fromKey(it) }.toSet()
            return EscalationStep(a, delayMs, tiers)
        }
    }

    private fun EscalationChain.toWire(): EscalationChainWire = EscalationChainWire(
        enabled = enabled,
        steps = steps.map {
            EscalationStepWire(
                action = it.action.key,
                delayMs = it.delayMs,
                triggerTiers = it.triggerTiers.map { tier -> tier.key }
            )
        },
        stopOnInteraction = stopOnInteraction,
        maxAttempts = maxAttempts
    )

    private data class QuietHoursWire(
        val enabled: Boolean = false,
        val startHour: Int = 22,
        val startMinute: Int = 0,
        val endHour: Int = 7,
        val endMinute: Int = 0,
        val days: List<Int> = DayOfWeek.values().map { it.value },
        val priorityOverrideTiers: List<String> = listOf(UrgencyTier.CRITICAL.key)
    ) {
        fun toDomain(): QuietHoursWindow = QuietHoursWindow(
            enabled = enabled,
            start = LocalTime.of(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59)),
            end = LocalTime.of(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59)),
            days = days.mapNotNull {
                runCatching { DayOfWeek.of(it) }.getOrNull()
            }.toSet(),
            priorityOverrideTiers = priorityOverrideTiers.mapNotNull { UrgencyTier.fromKey(it) }.toSet()
        )
    }

    private fun QuietHoursWindow.toWire(): QuietHoursWire = QuietHoursWire(
        enabled = enabled,
        startHour = start.hour,
        startMinute = start.minute,
        endHour = end.hour,
        endMinute = end.minute,
        days = days.map { it.value }.sorted(),
        priorityOverrideTiers = priorityOverrideTiers.map { it.key }
    )

    companion object {
        /** For non-DI callers (tests, quick tooling). */
        val DEFAULT = NotificationProfileResolver(Gson())

        /** Type token to round-trip a list of profile IDs if ever needed. */
        @Suppress("unused")
        val PROFILE_ID_LIST_TYPE = object : TypeToken<List<Long>>() {}.type
    }
}
