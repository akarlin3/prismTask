package com.averycorp.prismtask.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.NdPreferencesDataStore
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val themePreferences: ThemePreferences,
    private val ndPreferencesDataStore: NdPreferencesDataStore,
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean> = onboardingPreferences.hasCompletedOnboarding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _signInState = MutableStateFlow<SignInState>(SignInState.NotSignedIn)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    val themeMode: StateFlow<String> = themePreferences.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor: StateFlow<String> = themePreferences.getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#2563EB")

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
                    _signInState.value = SignInState.Error(it.message ?: "Sign-in failed")
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

    fun completeOnboarding() {
        viewModelScope.launch {
            onboardingPreferences.setOnboardingCompleted()
        }
    }
}

sealed class SignInState {
    data object NotSignedIn : SignInState()
    data object Loading : SignInState()
    data class SignedIn(val email: String) : SignInState()
    data class Error(val message: String) : SignInState()
}
