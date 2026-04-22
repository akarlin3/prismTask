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

internal val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

@Singleton
class OnboardingPreferences
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val ONBOARDING_COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
        private val HAS_SHOWN_BATTERY_OPTIMIZATION_PROMPT =
            booleanPreferencesKey("has_shown_battery_optimization_prompt")
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

    /**
     * Whether we've already shown the Samsung/OEM battery-optimization
     * prompt. Used by MainActivity so the dialog appears at most once per
     * install, even if the user declines.
     */
    fun hasShownBatteryOptimizationPrompt(): Flow<Boolean> =
        context.onboardingDataStore.data.map { prefs ->
            prefs[HAS_SHOWN_BATTERY_OPTIMIZATION_PROMPT] ?: false
        }

    suspend fun setBatteryOptimizationPromptShown() {
        context.onboardingDataStore.edit { prefs ->
            prefs[HAS_SHOWN_BATTERY_OPTIMIZATION_PROMPT] = true
        }
    }

    /**
     * Restores onboarding state from a JSON backup. Unlike [setOnboardingCompleted]
     * (which stamps `completed_at` to `now`), this writes the exact original
     * timestamp so a restored install doesn't look like it just finished
     * onboarding. Used by [com.averycorp.prismtask.data.export.DataImporter].
     */
    suspend fun restoreImportedState(
        hasCompletedOnboarding: Boolean,
        onboardingCompletedAt: Long,
        hasShownBatteryOptimizationPrompt: Boolean
    ) {
        context.onboardingDataStore.edit { prefs ->
            prefs[HAS_COMPLETED_ONBOARDING] = hasCompletedOnboarding
            prefs[ONBOARDING_COMPLETED_AT] = onboardingCompletedAt
            prefs[HAS_SHOWN_BATTERY_OPTIMIZATION_PROMPT] = hasShownBatteryOptimizationPrompt
        }
    }

    /** Debug-only: clear the onboarding flag so the tutorial plays again. */
    suspend fun resetOnboarding() {
        context.onboardingDataStore.edit { prefs ->
            prefs.remove(HAS_COMPLETED_ONBOARDING)
            prefs.remove(ONBOARDING_COMPLETED_AT)
            prefs.remove(HAS_SHOWN_BATTERY_OPTIMIZATION_PROMPT)
        }
    }
}
