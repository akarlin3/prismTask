package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val ONBOARDING_COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
    }

    fun hasCompletedOnboarding(): Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[HAS_COMPLETED_ONBOARDING] ?: false
    }

    fun getOnboardingCompletedAt(): Flow<Long> = context.onboardingDataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED_AT] ?: 0L
    }

    suspend fun setOnboardingCompleted() {
        context.onboardingDataStore.edit { prefs ->
            prefs[HAS_COMPLETED_ONBOARDING] = true
            prefs[ONBOARDING_COMPLETED_AT] = System.currentTimeMillis()
        }
    }
}
