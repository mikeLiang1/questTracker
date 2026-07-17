package com.mikeliang.questtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mikeliang.questtracker.health.HealthSyncScheduler
import com.mikeliang.questtracker.ui.questlist.QuestListScreen
import com.mikeliang.questtracker.ui.theme.QuestTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
