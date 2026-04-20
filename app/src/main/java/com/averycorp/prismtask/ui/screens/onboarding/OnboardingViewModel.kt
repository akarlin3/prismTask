package com.averycorp.prismtask.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.data.preferences.NdPreferencesDataStore
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
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
    private val leisurePreferences: LeisurePreferences,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val logger: PrismSyncLogger
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
                    checkExistingUserAndMaybeSkip()
                },
                onFailure = {
                    _signInState.value = SignInState.Error("Sign-in failed")
                }
            )
        }
    }

    private suspend fun checkExistingUserAndMaybeSkip() {
        val uid = authManager.userId ?: return
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("tasks")
                .limit(1).get().await()
            if (!snapshot.isEmpty) {
                logger.info(operation = "onboarding.check", detail = "existing=true skipping")
                // Write ordering invariant (v1.4.0 SoD skip-race fix):
                // `onboardingPreferences.setOnboardingCompleted()` is the write whose
                // DataStore emission flips `hasCompletedOnboarding` to true in
                // MainActivity and re-keys the SoD / tier-onboarding gate
                // LaunchedEffects. Any preference those gates read MUST already be
                // persisted by the time that emission fires — which means any such
                // flag write MUST come before `setOnboardingCompleted()` in this
                // block. Keep `setOnboardingCompleted()` last among the preference
                // writes; `_signInState` stays at the very end.
                taskBehaviorPreferences.setHasSetStartOfDay(true)
                userPreferencesDataStore.markTierOnboardingShown()
                onboardingPreferences.setOnboardingCompleted()
                _signInState.value = SignInState.ExistingUserDetected
            } else {
                logger.info(operation = "onboarding.check", detail = "existing=false routing=onboarding")
            }
        } catch (e: Exception) {
            logger.error(
                operation = "onboarding.check",
                detail = "failed fallback=onboarding error=${e.message}",
                throwable = e
            )
            // Remain in SignedIn state — user proceeds through normal onboarding.
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
            // Write the flag first so it is durable before viewModelScope is
            // cancelled by the navigation that fires immediately after this call.
            try {
                onboardingPreferences.setOnboardingCompleted()
                logger.info(operation = "onboarding.flag_written", status = "success")
            } catch (e: Exception) {
                logger.error(operation = "onboarding.flag_written", throwable = e)
            }
            // Template selections are non-critical — if viewModelScope is
            // cancelled here the user sees default prefs, not an onboarding loop.
            try {
                applyTemplateSelections(_templateSelections.value)
                logger.info(operation = "onboarding.templates_applied", status = "success")
            } catch (e: Exception) {
                logger.error(operation = "onboarding.templates_applied", throwable = e)
            }
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

    data object ExistingUserDetected : SignInState()
}
