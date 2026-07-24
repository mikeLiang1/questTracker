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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.health.HealthSyncScheduler
import com.mikeliang.questtracker.onboarding.OnboardingStateStore
import com.mikeliang.questtracker.reminders.ReminderPermissions
import com.mikeliang.questtracker.ui.onboarding.OnboardingScreen
import com.mikeliang.questtracker.ui.profile.ProfileScreen
import com.mikeliang.questtracker.ui.questdetail.QuestDetailScreen
import com.mikeliang.questtracker.ui.questlist.QuestListScreen
import com.mikeliang.questtracker.ui.reflection.ReflectionScreen
import com.mikeliang.questtracker.ui.questlog.QuestLogScreen
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler
    @Inject lateinit var onboardingStateStore: OnboardingStateStore
    @Inject lateinit var reminderPermissions: ReminderPermissions

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
        // Shown when reminders can't be scheduled exactly — the Android 12+ "Alarms &
        // reminders" access, which is off by default on 14+. Without it the OS batches
        // our alarms, so they arrive late or, on aggressive OEMs, effectively never.
        var showExactAlarmPrompt by remember { mutableStateOf(false) }

        // Both reminder asks fire at the same moment: after onboarding on a fresh
        // install, at launch for a returning user. Neither gates anything — a decline
        // just means quieter or less punctual reminders, never a blocked app.
        fun askReminderPermissions() {
            maybeRequestNotificationPermission()
            if (!reminderPermissions.canScheduleExactAlarms()) showExactAlarmPrompt = true
        }

        LaunchedEffect(Unit) {
            val complete = onboardingStateStore.isOnboardingComplete()
            needsOnboarding = !complete
            // Returning users keep the launch-time asks.
            if (complete) askReminderPermissions()
        }
        when (needsOnboarding) {
            null -> Unit // one blank frame while DataStore resolves
            true -> OnboardingScreen(onFinished = {
                needsOnboarding = false
                askReminderPermissions()
            })
            false -> HomeScaffold()
        }

        if (showExactAlarmPrompt) {
            ExactAlarmPrompt(
                onAllow = {
                    showExactAlarmPrompt = false
                    openExactAlarmSettings()
                },
                onDismiss = { showExactAlarmPrompt = false },
            )
        }
    }

    /**
     * One-time, dismissible nudge toward exact-alarm access. The copy is factual with no
     * shame, and "Not now" is a first-class choice — §8's rule that the app never coerces.
     * It is `remember`, not persisted, so it reappears on a later launch only while the
     * access is still missing and never once it is granted: a user who declines is never
     * permanently stuck with late reminders.
     */
    @Composable
    private fun ExactAlarmPrompt(onAllow: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Get reminders on time") },
            text = {
                Text(
                    "To have your quest reminders arrive at the exact time you set, allow " +
                        "“Alarms & reminders” for Quest. Without it, Android can delay them.",
                )
            },
            confirmButton = { TextButton(onClick = onAllow) { Text("Allow") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        )
    }

    /**
     * Opens the system "Alarms & reminders" screen for this app. The intent is null below
     * Android 12 (the access doesn't exist there) and a missing settings activity is
     * swallowed — either way reminders simply stay on the inexact fallback.
     */
    private fun openExactAlarmSettings() {
        val intent = reminderPermissions.exactAlarmSettingsIntent() ?: return
        runCatching { startActivity(intent) }
    }

    override fun onStart() {
        super.onStart()
        // Phase 4 policy: every app open reconciles the last 48h of health data.
        healthSyncScheduler.reconcileNow()
    }

    /**
     * The app's destinations. Plain state, no nav library — three tabs and two
     * overlay layers don't need one. A non-null [openQuestId] shows the quest detail
     * over the current tab; [showReflection] shows the monthly reflection the same
     * way. Back inside the reflection skips it (its own BackHandler); a tab tap just
     * puts it away without skipping, so the banner stays armed.
     *
     * `openQuestDay` (epoch day, saveable) rides alongside `openQuestId` when detail
     * is opened from a Quest Log row: that day's journal is what detail shows. The
     * board and profile have no day, so they clear it.
     */
    private enum class HomeTab { Today, Log, Profile }

    @Composable
    private fun HomeScaffold() {
        var tab by rememberSaveable { mutableStateOf(HomeTab.Today) }
        // Retains each tab's saveable state (notably the Quest Log scroll position) while
        // it is off-screen behind the quest-detail overlay, so returning from detail lands
        // back where you were rather than scrolled to the top.
        val tabStateHolder = rememberSaveableStateHolder()
        var openQuestId by rememberSaveable { mutableStateOf<String?>(null) }
        var openQuestDay by rememberSaveable { mutableStateOf<Long?>(null) }
        var showReflection by rememberSaveable { mutableStateOf(false) }

        fun openQuest(id: QuestId, day: LocalDate?) {
            openQuestId = id.value
            openQuestDay = day?.toEpochDay()
        }

        fun closeQuest() {
            openQuestId = null
            openQuestDay = null
        }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == HomeTab.Today,
                        onClick = {
                            tab = HomeTab.Today
                            closeQuest()
                            showReflection = false
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Today") },
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Log,
                        onClick = {
                            tab = HomeTab.Log
                            closeQuest()
                            showReflection = false
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Log") },
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.Profile,
                        onClick = {
                            tab = HomeTab.Profile
                            closeQuest()
                            showReflection = false
                        },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("Profile") },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val questId = openQuestId
                when {
                    showReflection -> ReflectionScreen(onClose = { showReflection = false })
                    questId != null -> {
                        BackHandler { closeQuest() }
                        QuestDetailScreen(
                            questId = QuestId(questId),
                            journalDay = openQuestDay?.let(LocalDate::ofEpochDay),
                            onClose = { closeQuest() },
                        )
                    }
                    else -> tabStateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            HomeTab.Today -> QuestListScreen(
                                onOpenQuest = { openQuest(it, null) },
                                onOpenReflection = { showReflection = true },
                            )
                            HomeTab.Log -> QuestLogScreen(
                                onOpenQuest = { id, day -> openQuest(id, day) },
                            )
                            HomeTab.Profile -> ProfileScreen(
                                onOpenChapter = { openQuest(it, null) },
                            )
                        }
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
