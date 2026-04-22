package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class CustomLeisureActivity(
    val id: String,
    val label: String,
    val icon: String
)

enum class LeisureSlotId { MUSIC, FLEX }

data class LeisureSlotConfig(
    val enabled: Boolean,
    val label: String,
    val emoji: String,
    val durationMinutes: Int,
    val gridColumns: Int,
    val autoComplete: Boolean,
    val hiddenBuiltInIds: List<String>,
    val customActivities: List<CustomLeisureActivity>
) {
    companion object {
        fun defaultFor(slot: LeisureSlotId): LeisureSlotConfig = when (slot) {
            LeisureSlotId.MUSIC -> LeisureSlotConfig(
                enabled = true,
                label = "Music Practice",
                emoji = "\uD83C\uDFB5",
                durationMinutes = 15,
                gridColumns = 3,
                autoComplete = true,
                hiddenBuiltInIds = emptyList(),
                customActivities = emptyList()
            )
            LeisureSlotId.FLEX -> LeisureSlotConfig(
                enabled = true,
                label = "Flexible",
                emoji = "\uD83C\uDFB2",
                durationMinutes = 30,
                gridColumns = 2,
                autoComplete = true,
                hiddenBuiltInIds = emptyList(),
                customActivities = emptyList()
            )
        }
    }
}

/**
 * User-added leisure section that lives alongside the built-in MUSIC / FLEX
 * slots. Unlike built-ins, a custom section has no seeded activity list — its
 * options are only the user-added [customActivities].
 */
data class CustomLeisureSection(
    val id: String,
    val label: String,
    val emoji: String,
    val enabled: Boolean,
    val durationMinutes: Int,
    val gridColumns: Int,
    val autoComplete: Boolean,
    val customActivities: List<CustomLeisureActivity>
)

internal val Context.leisureDataStore: DataStore<Preferences> by preferencesDataStore(name = "leisure_prefs")

@Singleton
class LeisurePreferences
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val activityListType = object : TypeToken<List<CustomLeisureActivity>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val sectionListType = object : TypeToken<List<CustomLeisureSection>>() {}.type

    companion object {
        private val CUSTOM_MUSIC_KEY = stringPreferencesKey("custom_music_activities")
        private val CUSTOM_FLEX_KEY = stringPreferencesKey("custom_flex_activities")
        private val CUSTOM_SECTIONS_KEY = stringPreferencesKey("custom_sections")

        private val MUSIC_ENABLED_KEY = stringPreferencesKey("music_enabled")
        private val FLEX_ENABLED_KEY = stringPreferencesKey("flex_enabled")

        private val MUSIC_LABEL_KEY = stringPreferencesKey("music_label")
        private val FLEX_LABEL_KEY = stringPreferencesKey("flex_label")

        private val MUSIC_EMOJI_KEY = stringPreferencesKey("music_emoji")
        private val FLEX_EMOJI_KEY = stringPreferencesKey("flex_emoji")

        private val MUSIC_DURATION_KEY = stringPreferencesKey("music_duration_minutes")
        private val FLEX_DURATION_KEY = stringPreferencesKey("flex_duration_minutes")

        private val MUSIC_COLUMNS_KEY = stringPreferencesKey("music_grid_columns")
        private val FLEX_COLUMNS_KEY = stringPreferencesKey("flex_grid_columns")

        private val MUSIC_AUTO_KEY = stringPreferencesKey("music_auto_complete")
        private val FLEX_AUTO_KEY = stringPreferencesKey("flex_auto_complete")

        private val MUSIC_HIDDEN_KEY = stringPreferencesKey("music_hidden_builtins")
        private val FLEX_HIDDEN_KEY = stringPreferencesKey("flex_hidden_builtins")

        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 240
        const val MIN_GRID_COLUMNS = 1
        const val MAX_GRID_COLUMNS = 4
    }

    fun getSlotConfig(slot: LeisureSlotId): Flow<LeisureSlotConfig> =
        context.leisureDataStore.data.map { prefs -> readSlotConfig(prefs, slot) }

    private fun readSlotConfig(prefs: Preferences, slot: LeisureSlotId): LeisureSlotConfig {
        val default = LeisureSlotConfig.defaultFor(slot)
        val keys = keysFor(slot)
        val hidden: List<String> = prefs[keys.hiddenKey]?.let { gson.fromJson<List<String>>(it, stringListType) } ?: emptyList()
        val custom: List<CustomLeisureActivity> = prefs[keys.customKey]?.let {
            gson.fromJson<List<CustomLeisureActivity>>(it, activityListType)
        } ?: emptyList()
        return LeisureSlotConfig(
            enabled = prefs[keys.enabledKey]?.toBooleanStrictOrNull() ?: default.enabled,
            label = prefs[keys.labelKey]?.takeIf { it.isNotBlank() } ?: default.label,
            emoji = prefs[keys.emojiKey]?.takeIf { it.isNotBlank() } ?: default.emoji,
            durationMinutes = prefs[keys.durationKey]?.toIntOrNull()
                ?.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
                ?: default.durationMinutes,
            gridColumns = prefs[keys.columnsKey]?.toIntOrNull()
                ?.coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
                ?: default.gridColumns,
            autoComplete = prefs[keys.autoKey]?.toBooleanStrictOrNull() ?: default.autoComplete,
            hiddenBuiltInIds = hidden,
            customActivities = custom
        )
    }

    suspend fun updateSlotConfig(
        slot: LeisureSlotId,
        enabled: Boolean? = null,
        label: String? = null,
        emoji: String? = null,
        durationMinutes: Int? = null,
        gridColumns: Int? = null,
        autoComplete: Boolean? = null
    ) {
        val keys = keysFor(slot)
        context.leisureDataStore.edit { prefs ->
            enabled?.let { prefs[keys.enabledKey] = it.toString() }
            label?.let {
                val trimmed = it.trim()
                if (trimmed.isNotEmpty()) prefs[keys.labelKey] = trimmed
            }
            emoji?.let {
                val trimmed = it.trim()
                if (trimmed.isNotEmpty()) prefs[keys.emojiKey] = trimmed
            }
            durationMinutes?.let {
                prefs[keys.durationKey] = it
                    .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
                    .toString()
            }
            gridColumns?.let {
                prefs[keys.columnsKey] = it
                    .coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
                    .toString()
            }
            autoComplete?.let { prefs[keys.autoKey] = it.toString() }
        }
    }

    suspend fun setBuiltInHidden(slot: LeisureSlotId, builtInId: String, hidden: Boolean) {
        val keys = keysFor(slot)
        context.leisureDataStore.edit { prefs ->
            val current: List<String> = prefs[keys.hiddenKey]?.let { gson.fromJson(it, stringListType) } ?: emptyList()
            val updated = if (hidden) (current + builtInId).distinct() else current.filter { it != builtInId }
            prefs[keys.hiddenKey] = gson.toJson(updated)
        }
    }

    suspend fun resetSlotConfig(slot: LeisureSlotId) {
        val keys = keysFor(slot)
        context.leisureDataStore.edit { prefs ->
            prefs.remove(keys.enabledKey)
            prefs.remove(keys.labelKey)
            prefs.remove(keys.emojiKey)
            prefs.remove(keys.durationKey)
            prefs.remove(keys.columnsKey)
            prefs.remove(keys.autoKey)
            prefs.remove(keys.hiddenKey)
        }
    }

    suspend fun addActivity(slot: LeisureSlotId, label: String, icon: String) {
        val key = keysFor(slot).customKey
        context.leisureDataStore.edit { prefs ->
            val current: List<CustomLeisureActivity> =
                prefs[key]?.let { gson.fromJson(it, activityListType) } ?: emptyList()
            val id = "custom_${slot.name.lowercase()}_${System.currentTimeMillis()}"
            prefs[key] = gson.toJson(current + CustomLeisureActivity(id, label, icon))
        }
    }

    suspend fun removeActivity(slot: LeisureSlotId, id: String) {
        val key = keysFor(slot).customKey
        context.leisureDataStore.edit { prefs ->
            val current: List<CustomLeisureActivity> =
                prefs[key]?.let { gson.fromJson(it, activityListType) } ?: emptyList()
            prefs[key] = gson.toJson(current.filter { it.id != id })
        }
    }

    fun getCustomMusicActivities(): Flow<List<CustomLeisureActivity>> =
        context.leisureDataStore.data.map { prefs ->
            prefs[CUSTOM_MUSIC_KEY]?.let { gson.fromJson<List<CustomLeisureActivity>>(it, activityListType) } ?: emptyList()
        }

    fun getCustomFlexActivities(): Flow<List<CustomLeisureActivity>> =
        context.leisureDataStore.data.map { prefs ->
            prefs[CUSTOM_FLEX_KEY]?.let { gson.fromJson<List<CustomLeisureActivity>>(it, activityListType) } ?: emptyList()
        }

    suspend fun addMusicActivity(label: String, icon: String) = addActivity(LeisureSlotId.MUSIC, label, icon)

    suspend fun addFlexActivity(label: String, icon: String) = addActivity(LeisureSlotId.FLEX, label, icon)

    suspend fun removeMusicActivity(id: String) = removeActivity(LeisureSlotId.MUSIC, id)

    suspend fun removeFlexActivity(id: String) = removeActivity(LeisureSlotId.FLEX, id)

    fun getCustomSections(): Flow<List<CustomLeisureSection>> =
        context.leisureDataStore.data.map { prefs -> readCustomSections(prefs) }

    private fun readCustomSections(prefs: Preferences): List<CustomLeisureSection> =
        prefs[CUSTOM_SECTIONS_KEY]?.let {
            runCatching { gson.fromJson<List<CustomLeisureSection>>(it, sectionListType) }
                .getOrNull()
        } ?: emptyList()

    /**
     * Adds a new custom section. Returns the generated id so callers can
     * immediately reference it.
     */
    suspend fun addCustomSection(label: String, emoji: String): String {
        val trimmedLabel = label.trim().ifEmpty { "New Section" }
        val trimmedEmoji = emoji.trim().ifEmpty { "\u2728" }
        val id = "custom_section_${System.currentTimeMillis()}"
        context.leisureDataStore.edit { prefs ->
            val current = readCustomSections(prefs)
            val section = CustomLeisureSection(
                id = id,
                label = trimmedLabel,
                emoji = trimmedEmoji,
                enabled = true,
                durationMinutes = 15,
                gridColumns = 2,
                autoComplete = true,
                customActivities = emptyList()
            )
            prefs[CUSTOM_SECTIONS_KEY] = gson.toJson(current + section)
        }
        return id
    }

    suspend fun removeCustomSection(id: String) {
        context.leisureDataStore.edit { prefs ->
            val current = readCustomSections(prefs)
            prefs[CUSTOM_SECTIONS_KEY] = gson.toJson(current.filter { it.id != id })
        }
    }

    suspend fun updateCustomSection(
        id: String,
        enabled: Boolean? = null,
        label: String? = null,
        emoji: String? = null,
        durationMinutes: Int? = null,
        gridColumns: Int? = null,
        autoComplete: Boolean? = null
    ) {
        context.leisureDataStore.edit { prefs ->
            val current = readCustomSections(prefs)
            val updated = current.map { section ->
                if (section.id != id) return@map section
                section.copy(
                    enabled = enabled ?: section.enabled,
                    label = label?.trim()?.takeIf { it.isNotEmpty() } ?: section.label,
                    emoji = emoji?.trim()?.takeIf { it.isNotEmpty() } ?: section.emoji,
                    durationMinutes = durationMinutes
                        ?.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
                        ?: section.durationMinutes,
                    gridColumns = gridColumns
                        ?.coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
                        ?: section.gridColumns,
                    autoComplete = autoComplete ?: section.autoComplete
                )
            }
            prefs[CUSTOM_SECTIONS_KEY] = gson.toJson(updated)
        }
    }

    suspend fun addCustomSectionActivity(sectionId: String, label: String, icon: String) {
        val trimmedLabel = label.trim()
        val trimmedIcon = icon.trim()
        if (trimmedLabel.isEmpty() || trimmedIcon.isEmpty()) return
        context.leisureDataStore.edit { prefs ->
            val current = readCustomSections(prefs)
            val updated = current.map { section ->
                if (section.id != sectionId) return@map section
                val activity = CustomLeisureActivity(
                    id = "custom_${sectionId}_${System.currentTimeMillis()}",
                    label = trimmedLabel,
                    icon = trimmedIcon
                )
                section.copy(customActivities = section.customActivities + activity)
            }
            prefs[CUSTOM_SECTIONS_KEY] = gson.toJson(updated)
        }
    }

    suspend fun removeCustomSectionActivity(sectionId: String, activityId: String) {
        context.leisureDataStore.edit { prefs ->
            val current = readCustomSections(prefs)
            val updated = current.map { section ->
                if (section.id != sectionId) return@map section
                section.copy(customActivities = section.customActivities.filter { it.id != activityId })
            }
            prefs[CUSTOM_SECTIONS_KEY] = gson.toJson(updated)
        }
    }

    suspend fun clearAll() {
        context.leisureDataStore.edit { it.clear() }
    }

    private data class SlotKeys(
        val enabledKey: Preferences.Key<String>,
        val labelKey: Preferences.Key<String>,
        val emojiKey: Preferences.Key<String>,
        val durationKey: Preferences.Key<String>,
        val columnsKey: Preferences.Key<String>,
        val autoKey: Preferences.Key<String>,
        val hiddenKey: Preferences.Key<String>,
        val customKey: Preferences.Key<String>
    )

    private fun keysFor(slot: LeisureSlotId): SlotKeys = when (slot) {
        LeisureSlotId.MUSIC -> SlotKeys(
            MUSIC_ENABLED_KEY,
            MUSIC_LABEL_KEY,
            MUSIC_EMOJI_KEY,
            MUSIC_DURATION_KEY,
            MUSIC_COLUMNS_KEY,
            MUSIC_AUTO_KEY,
            MUSIC_HIDDEN_KEY,
            CUSTOM_MUSIC_KEY
        )
        LeisureSlotId.FLEX -> SlotKeys(
            FLEX_ENABLED_KEY,
            FLEX_LABEL_KEY,
            FLEX_EMOJI_KEY,
            FLEX_DURATION_KEY,
            FLEX_COLUMNS_KEY,
            FLEX_AUTO_KEY,
            FLEX_HIDDEN_KEY,
            CUSTOM_FLEX_KEY
        )
    }
}
