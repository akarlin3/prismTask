package com.averycorp.prismtask.domain.model.notifications

/**
 * Catalog of built-in sound choices organized by [SoundCategory].
 *
 * Resource IDs are referenced by name via [resourceName]; the helper
 * `NotificationHelper` resolves these to `R.raw.*` at play time so the
 * domain module stays Android-free.
 *
 * [DEFAULT_SYSTEM] is the sentinel representing "whatever the system
 * default notification sound is" — we emit it so the user can explicitly
 * opt back to system default from the picker without us hardcoding a
 * specific URI.
 */
data class BuiltInSound(
    val id: String,
    val displayName: String,
    val category: SoundCategory,
    /** Android raw resource name (without extension), or null for system default. */
    val resourceName: String?,
    /** Approximate duration in millis — used for preview buffer sizing. */
    val approxDurationMs: Long = 1_500L,
    /** Whether this entry represents the system default ringtone. */
    val isSystemDefault: Boolean = false,
    /** Whether this entry represents a silent channel ("No sound"). */
    val isSilent: Boolean = false
) {
    companion object {
        const val SYSTEM_DEFAULT_ID = "__system_default__"
        const val SILENT_ID = "__silent__"

        val DEFAULT_SYSTEM = BuiltInSound(
            id = SYSTEM_DEFAULT_ID,
            displayName = "System default",
            category = SoundCategory.CHIMES,
            resourceName = null,
            isSystemDefault = true
        )

        val SILENT = BuiltInSound(
            id = SILENT_ID,
            displayName = "Silent",
            category = SoundCategory.MINIMAL,
            resourceName = null,
            isSilent = true
        )

        /**
         * Complete catalog. These map 1:1 to `res/raw/*.ogg` files that
         * ship in the APK; missing resource names mean only metadata is
         * exposed (the actual file is provided in a later asset drop so
         * the picker can function even before audio is bundled).
         */
        val ALL: List<BuiltInSound> = listOf(
            DEFAULT_SYSTEM,
            SILENT,
            // Chimes
            BuiltInSound("chime_gentle", "Gentle chime", SoundCategory.CHIMES, "chime_gentle"),
            BuiltInSound("chime_morning", "Morning chime", SoundCategory.CHIMES, "chime_morning"),
            BuiltInSound("chime_wind", "Wind chime", SoundCategory.CHIMES, "chime_wind"),
            // Bells
            BuiltInSound("bell_classic", "Classic bell", SoundCategory.BELLS, "bell_classic"),
            BuiltInSound("bell_tibetan", "Tibetan bell", SoundCategory.BELLS, "bell_tibetan"),
            BuiltInSound("bell_desk", "Desk bell", SoundCategory.BELLS, "bell_desk"),
            // Nature
            BuiltInSound("nature_birds", "Morning birds", SoundCategory.NATURE, "nature_birds"),
            BuiltInSound("nature_water", "Water drop", SoundCategory.NATURE, "nature_water"),
            BuiltInSound("nature_wind", "Forest wind", SoundCategory.NATURE, "nature_wind"),
            // Sci-Fi
            BuiltInSound("scifi_beep", "Console beep", SoundCategory.SCI_FI, "scifi_beep"),
            BuiltInSound("scifi_pulse", "Energy pulse", SoundCategory.SCI_FI, "scifi_pulse"),
            BuiltInSound("scifi_alert", "Bridge alert", SoundCategory.SCI_FI, "scifi_alert"),
            // Minimal
            BuiltInSound("minimal_pop", "Pop", SoundCategory.MINIMAL, "minimal_pop"),
            BuiltInSound("minimal_tick", "Tick", SoundCategory.MINIMAL, "minimal_tick"),
            BuiltInSound("minimal_whisper", "Whisper", SoundCategory.MINIMAL, "minimal_whisper"),
            // Percussive
            BuiltInSound("perc_wood", "Wood block", SoundCategory.PERCUSSIVE, "perc_wood"),
            BuiltInSound("perc_clap", "Clap", SoundCategory.PERCUSSIVE, "perc_clap"),
            BuiltInSound("perc_tap", "Soft tap", SoundCategory.PERCUSSIVE, "perc_tap"),
            // Voice
            BuiltInSound("voice_chimebell", "Voice: Ding", SoundCategory.VOICE, "voice_chimebell"),
            BuiltInSound("voice_heyyou", "Voice: Hey there", SoundCategory.VOICE, "voice_heyyou"),
            BuiltInSound("voice_reminder", "Voice: Reminder", SoundCategory.VOICE, "voice_reminder")
        )

        fun byId(id: String?): BuiltInSound? = ALL.firstOrNull { it.id == id }

        fun byCategory(category: SoundCategory): List<BuiltInSound> =
            ALL.filter { it.category == category }
    }
}
