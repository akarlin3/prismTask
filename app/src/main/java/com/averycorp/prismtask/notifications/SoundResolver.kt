package com.averycorp.prismtask.notifications

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.averycorp.prismtask.data.local.entity.CustomSoundEntity
import com.averycorp.prismtask.domain.model.notifications.BuiltInSound

/**
 * Turns the platform-agnostic sound identifiers used by the domain layer
 * (strings like `chime_gentle`, `custom_42`, or `__system_default__`)
 * into concrete Android [Uri] values playable by
 * [android.media.RingtoneManager] or a [NotificationChannel].
 *
 * The resolver does **not** persist anything — it's a pure lookup that
 * takes a [Context] (for package name + resource resolution) and an
 * optional list of [CustomSoundEntity] so it can honor user-uploaded
 * sounds without hitting the DB.
 */
object SoundResolver {

    /**
     * Resolves [soundId] against:
     *   1. The silent sentinel → null (channel uses no sound)
     *   2. The system-default sentinel → RingtoneManager default
     *   3. A user-uploaded `custom_<id>` id → the stored file URI
     *   4. A built-in catalog id → `R.raw.<resourceName>`
     *
     * A resolved result of [SilentChoice] means the caller should
     * create a channel with no sound at all; a [UriChoice] carries the
     * playable URI.
     */
    fun resolve(
        context: Context,
        soundId: String?,
        customSounds: List<CustomSoundEntity> = emptyList()
    ): Resolved {
        if (soundId.isNullOrBlank()) return UriChoice(defaultNotificationUri())
        if (soundId == BuiltInSound.SILENT_ID) return SilentChoice

        CustomSoundEntity.parseId(soundId)?.let { id ->
            val match = customSounds.firstOrNull { it.id == id }
            if (match != null) {
                return UriChoice(Uri.parse(match.uri))
            }
            // Fall through to default if the uploaded file vanished.
            return UriChoice(defaultNotificationUri())
        }

        val builtIn = BuiltInSound.byId(soundId) ?: return UriChoice(defaultNotificationUri())
        if (builtIn.isSystemDefault || builtIn.resourceName == null) {
            return UriChoice(defaultNotificationUri())
        }

        val resId = context.resources.getIdentifier(
            builtIn.resourceName,
            "raw",
            context.packageName
        )
        if (resId == 0) {
            // Shipping default: the catalog advertises a sound whose bytes
            // aren't in the APK yet (asset drop lands in a follow-up). Use
            // the system default URI rather than silently crashing.
            return UriChoice(defaultNotificationUri())
        }
        val uri = Uri.parse("android.resource://${context.packageName}/$resId")
        return UriChoice(uri)
    }

    private fun defaultNotificationUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Uri.EMPTY

    sealed class Resolved
    object SilentChoice : Resolved()
    data class UriChoice(val uri: Uri) : Resolved()
}
