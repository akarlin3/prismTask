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

private val Context.leisureDataStore: DataStore<Preferences> by preferencesDataStore(name = "leisure_prefs")

@Singleton
class LeisurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    companion object {
        private val CUSTOM_MUSIC_KEY = stringPreferencesKey("custom_music_activities")
        private val CUSTOM_FLEX_KEY = stringPreferencesKey("custom_flex_activities")
    }

    private val listType = object : TypeToken<List<CustomLeisureActivity>>() {}.type

    fun getCustomMusicActivities(): Flow<List<CustomLeisureActivity>> =
        context.leisureDataStore.data.map { prefs ->
            val json = prefs[CUSTOM_MUSIC_KEY] ?: "[]"
            gson.fromJson(json, listType)
        }

    fun getCustomFlexActivities(): Flow<List<CustomLeisureActivity>> =
        context.leisureDataStore.data.map { prefs ->
            val json = prefs[CUSTOM_FLEX_KEY] ?: "[]"
            gson.fromJson(json, listType)
        }

    suspend fun addMusicActivity(label: String, icon: String) {
        context.leisureDataStore.edit { prefs ->
            val current: List<CustomLeisureActivity> =
                gson.fromJson(prefs[CUSTOM_MUSIC_KEY] ?: "[]", listType)
            val id = "custom_music_${System.currentTimeMillis()}"
            val updated = current + CustomLeisureActivity(id, label, icon)
            prefs[CUSTOM_MUSIC_KEY] = gson.toJson(updated)
        }
    }

    suspend fun addFlexActivity(label: String, icon: String) {
        context.leisureDataStore.edit { prefs ->
            val current: List<CustomLeisureActivity> =
                gson.fromJson(prefs[CUSTOM_FLEX_KEY] ?: "[]", listType)
            val id = "custom_flex_${System.currentTimeMillis()}"
            val updated = current + CustomLeisureActivity(id, label, icon)
            prefs[CUSTOM_FLEX_KEY] = gson.toJson(updated)
        }
    }

    suspend fun removeMusicActivity(id: String) {
        context.leisureDataStore.edit { prefs ->
            val current: List<CustomLeisureActivity> =
                gson.fromJson(prefs[CUSTOM_MUSIC_KEY] ?: "[]", listType)
            prefs[CUSTOM_MUSIC_KEY] = gson.toJson(current.filter { it.id != id })
        }
    }

    suspend fun removeFlexActivity(id: String) {
        context.leisureDataStore.edit { prefs ->
            val current: List<CustomLeisureActivity> =
                gson.fromJson(prefs[CUSTOM_FLEX_KEY] ?: "[]", listType)
            prefs[CUSTOM_FLEX_KEY] = gson.toJson(current.filter { it.id != id })
        }
    }

    suspend fun clearAll() {
        context.leisureDataStore.edit { it.clear() }
    }
}
