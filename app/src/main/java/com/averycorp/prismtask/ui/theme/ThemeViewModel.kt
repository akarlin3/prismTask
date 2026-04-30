package com.averycorp.prismtask.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.widget.WidgetUpdateManager
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
    private val themePreferences: ThemePreferences,
    private val widgetUpdateManager: WidgetUpdateManager
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
     * The optional widget-only theme override. `null` means widgets follow
     * the app theme; a non-null value pins widgets to that [PrismTheme]
     * regardless of the in-app selection.
     */
    val widgetThemeOverride: StateFlow<PrismTheme?> = themePreferences
        .getWidgetThemeOverride()
        .map { name -> if (name.isBlank()) null else parsePrismThemeOrNull(name) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    /**
     * Persist [theme] as the user's new selection. The backing flow will emit
     * the new value and drive the CompositionLocal update in MainActivity.
     */
    fun setTheme(theme: PrismTheme) {
        viewModelScope.launch {
            themePreferences.setPrismTheme(theme.name)
            // Glance widgets cache their last-rendered RemoteViews and never
            // observe DataStore on their own — push a refresh so home-screen
            // widgets adopt the new palette immediately rather than waiting
            // on the 15-min periodic worker.
            widgetUpdateManager.updateAllWidgets()
        }
    }

    /**
     * Sets the widget-only theme override. Pass `null` to clear the override
     * and let widgets fall back to the app theme.
     */
    fun setWidgetThemeOverride(theme: PrismTheme?) {
        viewModelScope.launch {
            themePreferences.setWidgetThemeOverride(theme?.name)
            widgetUpdateManager.updateAllWidgets()
        }
    }

    companion object {
        private fun parsePrismThemeOrDefault(name: String): PrismTheme =
            parsePrismThemeOrNull(name) ?: PrismTheme.VOID

        private fun parsePrismThemeOrNull(name: String): PrismTheme? =
            try {
                PrismTheme.valueOf(name)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
