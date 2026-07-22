package com.mikeliang.questtracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mikeliang.questtracker.health.HealthSyncScheduler
import com.mikeliang.questtracker.reminders.ReminderCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class QuestTrackerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler
    @Inject lateinit var reminderCoordinator: ReminderCoordinator

    /** Process-lifetime scope for the reminder sync collector. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** WorkManager is initialized on demand with this so @HiltWorker workers inject. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        healthSyncScheduler.ensurePeriodicSync()
        // Keep OS alarms in lockstep with quest/completion changes for as long as the
        // process lives; alarms also survive process death and are rebuilt on boot.
        appScope.launch { reminderCoordinator.keepInSync() }
    }
}
