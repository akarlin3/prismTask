package com.averycorp.prismtask.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.data.preferences.NdPreferencesDataStore
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.ui.screens.leisure.LeisureViewModel
import com.averycorp.prismtask.ui.screens.templates.TemplateSelections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val themePreferences: ThemePreferences,
    private val ndPreferencesDataStore: NdPreferencesDataStore,
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val taskRepository: TaskRepository,
    private val selfCareRepository: SelfCareRepository,
    private val leisurePreferences: LeisurePreferences
) : ViewModel() {
    val hasCompletedOnboarding: StateFlow<Boolean> = onboardingPreferences
        .hasCompletedOnboarding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _signInState = MutableStateFlow<SignInState>(SignInState.NotSignedIn)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    val themeMode: StateFlow<String> = themePreferences
        .getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor: StateFlow<String> = themePreferences
        .getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#2563EB")

    private val _templateSelections = MutableStateFlow(TemplateSelections())
    val templateSelections: StateFlow<TemplateSelections> = _templateSelections.asStateFlow()

    init {
        if (authManager.isSignedIn.value) {
            _signInState.value = SignInState.SignedIn(
                authManager.currentUser.value?.email ?: ""
            )
        }
    }

    fun onGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _signInState.value = SignInState.Loading
            val result = authManager.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    _signInState.value = SignInState.SignedIn(user.email ?: "")
                    syncService.startAutoSync()
                },
                onFailure = {
                    _signInState.value = SignInState.Error("Sign-in failed")
                }
            )
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { themePreferences.setAccentColor(hex) }
    }

    fun createQuickTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.addTask(title = title)
        }
    }

    fun setAdhdMode(enabled: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setAdhdMode(enabled) }
    }

    fun setCalmMode(enabled: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setCalmMode(enabled) }
    }

    fun setFocusReleaseMode(enabled: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setFocusReleaseMode(enabled) }
    }

    fun updateTemplateSelections(selections: TemplateSelections) {
        _templateSelections.value = selections
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            applyTemplateSelections(_templateSelections.value)
            onboardingPreferences.setOnboardingCompleted()
        }
    }

    /**
     * Persists the user's template picks. For leisure, any default not in the
     * selection is added to `hiddenBuiltInIds` so it won't appear in the
     * Leisure screen. For self-care / bedtime / housework, the effective step
     * ids are written to the DB via [SelfCareRepository.seedSelfCareSteps]
     * (idempotent, safe to run on an already-populated DB).
     */
    internal suspend fun applyTemplateSelections(selections: TemplateSelections) {
        applyLeisureSelection(
            LeisureSlotId.MUSIC,
            LeisureViewModel.DEFAULT_INSTRUMENTS.map { it.id },
            selections.musicIds
        )
        applyLeisureSelection(
            LeisureSlotId.FLEX,
            LeisureViewModel.DEFAULT_FLEX_OPTIONS.map { it.id },
            selections.flexIds
        )
        applyRoutineSelection("morning", selections)
        applyRoutineSelection("bedtime", selections)
        applyRoutineSelection("housework", selections)
    }

    private suspend fun applyLeisureSelection(
        slot: LeisureSlotId,
        defaultIds: List<String>,
        selectedIds: Set<String>
    ) {
        defaultIds.forEach { id ->
            leisurePreferences.setBuiltInHidden(slot, id, hidden = id !in selectedIds)
        }
    }

    private suspend fun applyRoutineSelection(routineType: String, selections: TemplateSelections) {
        val stepIds = selections.effectiveStepIds(routineType)
        if (stepIds.isEmpty()) return
        selfCareRepository.seedSelfCareSteps(routineType, stepIds.toList())
    }
}

sealed class SignInState {
    data object NotSignedIn : SignInState()

    data object Loading : SignInState()

    data class SignedIn(
        val email: String
    ) : SignInState()

    data class Error(
        val message: String
    ) : SignInState()
}
