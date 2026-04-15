package com.averycorp.prismtask.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.averycorp.prismtask.ui.navigation.routes.aiRoutes
import com.averycorp.prismtask.ui.navigation.routes.authRoutes
import com.averycorp.prismtask.ui.navigation.routes.feedbackRoutes
import com.averycorp.prismtask.ui.navigation.routes.habitRoutes
import com.averycorp.prismtask.ui.navigation.routes.modeRoutes
import com.averycorp.prismtask.ui.navigation.routes.notificationRoutes
import com.averycorp.prismtask.ui.navigation.routes.settingsSubScreenRoutes
import com.averycorp.prismtask.ui.navigation.routes.taskRoutes
import com.averycorp.prismtask.ui.navigation.routes.templateRoutes

/**
 * Feature route entry point, extracted from PrismTaskNavGraph.
 *
 * Destinations are grouped into per-domain files under
 * [com.averycorp.prismtask.ui.navigation.routes]. Each group function
 * registers its routes on the provided [NavGraphBuilder] receiver. Adding
 * a new destination means adding it to the appropriate domain file, not
 * growing this orchestrator.
 */
internal fun NavGraphBuilder.featureRoutes(
    navController: NavHostController,
    initialSharedText: String? = null
) {
    // Core domains
    taskRoutes(navController)
    habitRoutes(navController)
    modeRoutes(navController)
    aiRoutes(navController, initialSharedText)

    // Supporting domains
    authRoutes(navController)
    templateRoutes(navController)
    feedbackRoutes(navController)

    // Hub-style groupings
    settingsSubScreenRoutes(navController)
    notificationRoutes(navController)
}
