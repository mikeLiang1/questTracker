package org.example.project.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object DashboardRoutes : Route {
        @Serializable
        data object Home : Route

        @Serializable
        data object SearchRoutes : Route {
            @Serializable
            data object OverView : Route

            @Serializable
            data object Test : Route
        }

        @Serializable
        data object Profile : Route
    }
}

val appTopLevelRoutes = setOf(Route.DashboardRoutes)

val dashboardAllRoutes = setOf(
    Route.DashboardRoutes.Home,
    Route.DashboardRoutes.Profile,
    Route.DashboardRoutes.SearchRoutes
)

val searchTopLevel = setOf(
    Route.DashboardRoutes.SearchRoutes.OverView,
    Route.DashboardRoutes.SearchRoutes.Test
)

