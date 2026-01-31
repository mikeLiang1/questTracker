package org.example.project.features.dashboard.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.project.navigation.Route
import kotlin.to

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)

val dashboardTopLevelDestinations = mapOf(
    Route.DashboardRoutes.Home to BottomNavItem(
        label = "Home",
        icon = Icons.Filled.Home
    ),
    Route.DashboardRoutes.Profile to BottomNavItem(
        label = "Profile",
        icon = Icons.Filled.AccountCircle
    )
)
