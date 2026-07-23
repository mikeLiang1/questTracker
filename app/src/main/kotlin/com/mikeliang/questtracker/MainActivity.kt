package com.mikeliang.questtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.health.HealthSyncScheduler
import com.mikeliang.questtracker.onboarding.OnboardingStateStore
import com.mikeliang.questtracker.ui.onboarding.OnboardingScreen
import com.mikeliang.questtracker.ui.profile.ProfileScreen
import com.mikeliang.questtracker.ui.questdetail.QuestDetailScreen
import com.mikeliang.questtracker.ui.questlist.QuestListScreen
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler
    @Inject lateinit var onboardingStateStore: OnboardingStateStore

    // Notifications are optional: a denial just means reminders stay silent. We ask once
    // and never re-prompt or gate anything on the answer. Fresh installs are asked only
    // after onboarding completes — no system dialog over the onboarding flow.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuestTrackerTheme {
                Root()
            }
        }
    }

    /**
     * First-run gate: onboarding until its flag is set, then the two-tab home. Plain
     * Compose state — `onFinished` flips it directly, so there is no race against the
     * DataStore write. On recreation the flag is simply re-read.
     */
    @Composable
    private fun Root() {
        var needsOnboarding by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            val complete = onboardingStateStore.isOnboardingComplete()
            needsOnboarding = !complete
            // Returning users keep the launch-time ask.
            if (complete) maybeRequestNotificationPermission()
        }
        when (needsOnboarding) {
            null -> Unit // one blank frame while DataStore resolves
            true -> OnboardingScreen(onFinished = {
                needsOnboarding = false
                maybeRequestNotificationPermission()
            })
            false -> HomeScaffold()
        }
    }

    override fun onStart() {
        super.onStart()
        // Phase 4 policy: every app open reconciles the last 48h of health data.
        healthSyncScheduler.reconcileNow()
    }

    /**
     * The app's destinations. Plain state, no nav library — two tabs and one detail
     * layer don't need one. A non-null [openQuestId] shows the quest detail over the
     * current tab; back (or a tab tap) clears it.
     */
    private enum class HomeTab { Today, Profile }

    @Composable
    private fun HomeScaffold() {
        var tab by rememberSaveable { mutableStateOf(HomeTab.Today) }
        var openQuestId by rememberSaveable { mutableStateOf<String?>(null) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == HomeTab.Today,
                        onClick = {
                            tab = HomeTab.Today
                            openQuestId = null
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Today") },
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Profile,
                        onClick = {
                            tab = HomeTab.Profile
                            openQuestId = null
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("Profile") },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val questId = openQuestId
                if (questId != null) {
                    BackHandler { openQuestId = null }
                    QuestDetailScreen(
                        questId = QuestId(questId),
                        onClose = { openQuestId = null },
                    )
                } else {
                    when (tab) {
                        HomeTab.Today -> QuestListScreen(onOpenQuest = { openQuestId = it.value })
                        HomeTab.Profile -> ProfileScreen(onOpenChapter = { openQuestId = it.value })
                    }
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
