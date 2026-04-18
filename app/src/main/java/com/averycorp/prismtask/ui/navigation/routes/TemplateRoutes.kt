package com.averycorp.prismtask.ui.navigation.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.templates.AddEditTemplateScreen
import com.averycorp.prismtask.ui.screens.templates.TemplateBrowserScreen
import com.averycorp.prismtask.ui.screens.templates.TemplateListScreen

/**
 * Task / project / habit template route definitions.
 */
internal fun NavGraphBuilder.templateRoutes(navController: NavHostController) {
    horizontalSlideComposable(PrismTaskRoute.TemplateList.route) {
        TemplateListScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.TemplateBrowser.route) {
        TemplateBrowserScreen(navController)
    }

    composable(
        route = PrismTaskRoute.AddEditTemplate.route,
        arguments = listOf(
            navArgument("templateId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        ),
        enterTransition = fadeEnterOnly,
        exitTransition = fadeExitOnly,
        popEnterTransition = fadeEnterOnly,
        popExitTransition = fadeExitOnly
    ) {
        AddEditTemplateScreen(navController)
    }
}
