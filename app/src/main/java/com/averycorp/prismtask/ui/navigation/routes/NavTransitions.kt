package com.averycorp.prismtask.ui.navigation.routes

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Shared navigation-transition helpers used across every per-domain
 * route file. Extracted so individual route declarations stay compact
 * and the transition animations remain consistent.
 */

internal const val NAV_ANIM_DURATION = 300

/**
 * Full horizontal slide + fade — the default for most navigation
 * destinations (push onto stack from the right).
 */
internal val horizontalSlideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
        fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
}

internal val horizontalSlideExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
        fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
}

internal val horizontalSlidePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
        fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
}

internal val horizontalSlidePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
        fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
}

/**
 * Minimal horizontal slide without fade — used for a handful of
 * mode-shell routes that don't want the fade tail.
 */
internal val simpleSlideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION))
}

internal val simpleSlideExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION))
}

internal val simpleSlidePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION))
}

/**
 * Vertical slide + fade — used for modal-style full-screen dialogs
 * like bug report and feature request.
 */
internal val verticalSlideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInVertically(initialOffsetY = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
        fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
}

internal val verticalSlidePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutVertically(targetOffsetY = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
        fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
}

internal val fadeEnterOnly: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
}

internal val fadeExitOnly: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
}

/**
 * Register a composable destination that uses the standard horizontal
 * slide-plus-fade transitions. Reduces every route from ~20 lines of
 * transition boilerplate to a single call.
 */
internal fun NavGraphBuilder.horizontalSlideComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = horizontalSlideEnter,
        exitTransition = horizontalSlideExit,
        popEnterTransition = horizontalSlidePopEnter,
        popExitTransition = horizontalSlidePopExit,
        content = content
    )
}

/**
 * Register a composable destination that uses minimal horizontal
 * slide (no fade). Used by a small number of mode-shell routes.
 */
internal fun NavGraphBuilder.simpleSlideComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = simpleSlideEnter,
        exitTransition = simpleSlideExit,
        popEnterTransition = simpleSlidePopEnter,
        popExitTransition = simpleSlideExit,
        content = content
    )
}
