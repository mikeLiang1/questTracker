package org.example.project.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.budget.navigation.Navigator
import com.example.budget.navigation.rememberNavigationState
import com.example.budget.navigation.toEntries
import org.example.project.features.dashboard.navigation.DashboardNavigation


@Composable
fun AppNavigation(startDestination: NavKey = Route.DashboardRoutes) {
    val navigationState = rememberNavigationState(
        startRoute = startDestination,
        topLevelRoutes = appTopLevelRoutes
    )

    val navigator = remember { Navigator(navigationState) }


    val entryProvider = entryProvider<NavKey> {
        entry<Route.DashboardRoutes> {
            DashboardNavigation()
        }
//        entry<Route.LoginRoutes> {
//            LoginNavigation(
//                onLoginSuccess = {
//                    navigator.replaceRoot(Route.BottomRoutes)
//                }
//            )
//        }
    }

    NavDisplay(
        entries = navigationState.toEntries(
            entryProvider = entryProvider
        ),
        onBack = { navigator.goBack() }
    )
}
