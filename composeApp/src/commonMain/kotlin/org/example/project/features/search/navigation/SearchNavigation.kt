package org.example.project.features.search.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.budget.navigation.Navigator
import com.example.budget.navigation.rememberNavigationState
import com.example.budget.navigation.toEntries
import org.example.project.navigation.Route
import org.example.project.navigation.searchTopLevel

@Composable
fun SearchNavigation() {
    val navigationState = rememberNavigationState(
        startRoute = Route.DashboardRoutes.SearchRoutes.OverView,
        topLevelRoutes = searchTopLevel
    )

    val navigator = remember { Navigator(navigationState) }

    val entryProvider = entryProvider<NavKey> {
        entry<Route.DashboardRoutes.SearchRoutes.OverView> {
            Text("overview")
            Button(onClick = {navigator.navigate(Route.DashboardRoutes.SearchRoutes.Test)}) {
                Text("Button")
            }


        }
        entry<Route.DashboardRoutes.SearchRoutes.Test> {
            Text("test")
        }
    }


    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        entries = navigationState.toEntries(entryProvider),
        onBack = { navigator.goBack() }
    )

}


