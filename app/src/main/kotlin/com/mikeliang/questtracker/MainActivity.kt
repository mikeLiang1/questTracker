package com.mikeliang.questtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mikeliang.questtracker.health.HealthSyncScheduler
import com.mikeliang.questtracker.ui.profile.ProfileScreen
import com.mikeliang.questtracker.ui.questlist.QuestListScreen
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler

    // Notifications are optional: a denial just means reminders stay silent. We ask once
    // and never re-prompt or gate anything on the answer.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            QuestTrackerTheme {
                HomeScaffold()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Phase 4 policy: every app open reconciles the last 48h of health data.
        healthSyncScheduler.reconcileNow()
    }

    /** The app's two destinations. Plain state, no nav library — two tabs don't need one. */
    private enum class HomeTab { Today, Profile }

    @Composable
    private fun HomeScaffold() {
        var tab by rememberSaveable { mutableStateOf(HomeTab.Today) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == HomeTab.Today,
                        onClick = { tab = HomeTab.Today },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Today") },
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Profile,
                        onClick = { tab = HomeTab.Profile },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("Profile") },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (tab) {
                    HomeTab.Today -> QuestListScreen()
                    HomeTab.Profile -> ProfileScreen()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
