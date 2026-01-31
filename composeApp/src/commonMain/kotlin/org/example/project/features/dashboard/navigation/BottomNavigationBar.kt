package org.example.project.features.dashboard.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.budget.navigation.NavigationState
import com.example.budget.navigation.Navigator
import kotlin.collections.component1
import kotlin.collections.component2

@Composable
fun BottomNavigationBar(
    navigationState: NavigationState,
    navigator: Navigator
) {
    NavigationBar {
        dashboardTopLevelDestinations.forEach { (route, bottomNavItem) ->

            NavigationBarItem(
                icon = { Icon(bottomNavItem.icon, contentDescription = bottomNavItem.label) },
                label = { Text(bottomNavItem.label) },
                selected = navigationState.topLevelRoute == route,
                onClick = {
                    navigator.navigate(route)
                }
            )
        }
    }
}
