package com.averycorp.prismtask.ui.a11y

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reads the system-wide animator duration scale and returns true when the
 * user has effectively disabled animations (scale 0). Falls back to false
 * if the setting can't be read.
 *
 * This is the non-composable accessor; the composable form
 * [rememberReducedMotion] caches the value per composition.
 */
fun isSystemAnimationsDisabled(context: android.content.Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Throwable) {
        false
    }
}

/**
 * Composable accessor for the combined reduced-motion state: true if
 * either the user has disabled system animations OR turned on the
 * in-app Accessibility > Reduce Motion toggle (propagated via
 * [LocalReducedMotion]).
 *
 * Callers can branch on this to skip expensive or potentially
 * disorienting animations:
 *
 * ```
 * val reducedMotion = rememberReducedMotion()
 * val modifier = if (reducedMotion) Modifier else Modifier.animateContentSize()
 * ```
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val systemDisabled = remember(context) { isSystemAnimationsDisabled(context) }
    val appOverride = LocalReducedMotion.current
    return systemDisabled || appOverride
}

/** App-level reduce-motion override, fed from A11yPreferences at the root. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** App-level high-contrast toggle. */
val LocalHighContrast = staticCompositionLocalOf { false }

/** App-level "large touch targets" toggle. Callers can read this to bump
 *  minimum interactive sizes from the default 48dp to 56dp. */
val LocalLargeTouchTargets = staticCompositionLocalOf { false }

/**
 * Resolve the minimum touch-target dimension given the current
 * [LocalLargeTouchTargets] state. Defaults to 48dp, 56dp when large targets
 * are enabled.
 */
@Composable
fun rememberMinTouchTarget(): Dp {
    val large = LocalLargeTouchTargets.current
    return if (large) 56.dp else 48.dp
}

/** Convenience modifier that marks a composable as a heading for TalkBack
 *  navigation. Screen titles and section headers should use this. */
fun Modifier.asHeading(): Modifier = this.semantics { heading() }

/** Convenience modifier that marks a composable as a polite live region
 *  (announced by TalkBack when content changes). */
fun Modifier.politeLiveRegion(): Modifier = this.semantics {
    liveRegion = LiveRegionMode.Polite
}
