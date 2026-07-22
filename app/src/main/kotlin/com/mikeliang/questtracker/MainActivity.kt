package com.mikeliang.questtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mikeliang.questtracker.health.HealthSyncScheduler
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuestListScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Phase 4 policy: every app open reconciles the last 48h of health data.
        healthSyncScheduler.reconcileNow()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
