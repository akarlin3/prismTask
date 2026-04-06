package com.averykarlin.averytask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dashboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "dashboard_prefs")

@Singleton
class DashboardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SECTION_ORDER = stringPreferencesKey("section_order")
        private val HIDDEN_SECTIONS = stringSetPreferencesKey("hidden_sections")
        private val PROGRESS_STYLE = stringPreferencesKey("progress_style")

        val DEFAULT_ORDER = listOf("progress", "habits", "overdue", "today_tasks", "plan_more", "completed")
    }

    fun getSectionOrder(): Flow<List<String>> = context.dashboardDataStore.data.map { prefs ->
        prefs[SECTION_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_ORDER
    }

    fun getHiddenSections(): Flow<Set<String>> = context.dashboardDataStore.data.map { prefs ->
        prefs[HIDDEN_SECTIONS] ?: emptySet()
    }

    fun getProgressStyle(): Flow<String> = context.dashboardDataStore.data.map { prefs ->
        prefs[PROGRESS_STYLE] ?: "ring"
    }

    suspend fun setSectionOrder(order: List<String>) {
        context.dashboardDataStore.edit { prefs ->
            prefs[SECTION_ORDER] = order.joinToString(",")
        }
    }

    suspend fun setHiddenSections(hidden: Set<String>) {
        context.dashboardDataStore.edit { prefs ->
            prefs[HIDDEN_SECTIONS] = hidden
        }
    }

    suspend fun setProgressStyle(style: String) {
        context.dashboardDataStore.edit { prefs ->
            prefs[PROGRESS_STYLE] = style
        }
    }

    suspend fun resetToDefaults() {
        context.dashboardDataStore.edit { prefs ->
            prefs.remove(SECTION_ORDER)
            prefs.remove(HIDDEN_SECTIONS)
            prefs.remove(PROGRESS_STYLE)
        }
    }
}
