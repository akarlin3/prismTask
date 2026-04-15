package com.averycorp.prismtask.ui.navigation.routes

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.auth.AuthScreen
import com.averycorp.prismtask.ui.screens.auth.AuthViewModel

/**
 * Authentication route definitions. Onboarding lives in NavGraph itself
 * (it drives top-level navigation state), so only the Auth sign-in /
 * sign-up screen is routed here.
 */
internal fun NavGraphBuilder.authRoutes(navController: NavHostController) {
    horizontalSlideComposable(PrismTaskRoute.Auth.route) {
        val authViewModel: AuthViewModel = hiltViewModel()
        AuthScreen(
            viewModel = authViewModel,
            onContinue = { navController.popBackStack() }
        )
    }
}
