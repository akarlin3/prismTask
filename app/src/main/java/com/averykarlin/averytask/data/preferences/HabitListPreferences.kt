package com.averykarlin.averytask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.habitListDataStore: DataStore<Preferences> by preferencesDataStore(name = "habit_list_prefs")

data class BuiltInSortOrders(
    val morning: Int,
    val bedtime: Int,
    val medication: Int,
    val school: Int,
    val leisure: Int
)

@Singleton
class HabitListPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val MORNING_SORT_ORDER = intPreferencesKey("morning_sort_order")
        private val BEDTIME_SORT_ORDER = intPreferencesKey("bedtime_sort_order")
        private val MEDICATION_SORT_ORDER = intPreferencesKey("medication_sort_order")
        private val SCHOOL_SORT_ORDER = intPreferencesKey("school_sort_order")
        private val LEISURE_SORT_ORDER = intPreferencesKey("leisure_sort_order")
        const val DEFAULT_MORNING_ORDER = -5
        const val DEFAULT_BEDTIME_ORDER = -4
        const val DEFAULT_MEDICATION_ORDER = -3
        const val DEFAULT_SCHOOL_ORDER = -2
        const val DEFAULT_LEISURE_ORDER = -1
    }

    fun getAutoHabitSortOrders(): Flow<Triple<Int, Int, Int>> = context.habitListDataStore.data.map { prefs ->
        Triple(
            prefs[MORNING_SORT_ORDER] ?: DEFAULT_MORNING_ORDER,
            prefs[BEDTIME_SORT_ORDER] ?: DEFAULT_BEDTIME_ORDER,
            prefs[MEDICATION_SORT_ORDER] ?: DEFAULT_MEDICATION_ORDER
        )
    }

    fun getBuiltInSortOrders(): Flow<BuiltInSortOrders> = context.habitListDataStore.data.map { prefs ->
        BuiltInSortOrders(
            morning = prefs[MORNING_SORT_ORDER] ?: DEFAULT_MORNING_ORDER,
            bedtime = prefs[BEDTIME_SORT_ORDER] ?: DEFAULT_BEDTIME_ORDER,
            medication = prefs[MEDICATION_SORT_ORDER] ?: DEFAULT_MEDICATION_ORDER,
            school = prefs[SCHOOL_SORT_ORDER] ?: DEFAULT_SCHOOL_ORDER,
            leisure = prefs[LEISURE_SORT_ORDER] ?: DEFAULT_LEISURE_ORDER
        )
    }

    suspend fun setAutoHabitSortOrders(morningSortOrder: Int, bedtimeSortOrder: Int, medicationSortOrder: Int) {
        context.habitListDataStore.edit { prefs ->
            prefs[MORNING_SORT_ORDER] = morningSortOrder
            prefs[BEDTIME_SORT_ORDER] = bedtimeSortOrder
            prefs[MEDICATION_SORT_ORDER] = medicationSortOrder
        }
    }

    suspend fun setBuiltInSortOrders(orders: BuiltInSortOrders) {
        context.habitListDataStore.edit { prefs ->
            prefs[MORNING_SORT_ORDER] = orders.morning
            prefs[BEDTIME_SORT_ORDER] = orders.bedtime
            prefs[MEDICATION_SORT_ORDER] = orders.medication
            prefs[SCHOOL_SORT_ORDER] = orders.school
            prefs[LEISURE_SORT_ORDER] = orders.leisure
        }
    }

    suspend fun clearAll() {
        context.habitListDataStore.edit { it.clear() }
    }
}
