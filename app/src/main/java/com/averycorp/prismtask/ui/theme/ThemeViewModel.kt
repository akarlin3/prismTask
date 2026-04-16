package com.averycorp.prismtask.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel that exposes the currently-selected [PrismTheme] and persists
 * user selections via [ThemePreferences]. The app reads [currentTheme] at the
 * root of the Compose tree so that the CompositionLocal-provided palette and
 * fonts propagate to every screen.
 */
@HiltViewModel
class ThemeViewModel
@Inject
constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {
    /**
     * The [PrismTheme] the user has selected. Falls back to [PrismTheme.VOID]
     * both for first launch (no stored value) and for any malformed stored
     * value so the UI never crashes on an unknown enum name.
     */
    val currentTheme: StateFlow<PrismTheme> = themePreferences
        .getPrismTheme()
        .map { name -> parsePrismThemeOrDefault(name) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PrismTheme.VOID
        )

    /**
     * Persist [theme] as the user's new selection. The backing flow will emit
     * the new value and drive the CompositionLocal update in MainActivity.
     */
    fun setTheme(theme: PrismTheme) {
        viewModelScope.launch {
            themePreferences.setPrismTheme(theme.name)
        }
    }

    companion object {
        private fun parsePrismThemeOrDefault(name: String): PrismTheme =
            try {
                PrismTheme.valueOf(name)
            } catch (_: IllegalArgumentException) {
                PrismTheme.VOID
            }
    }
}
