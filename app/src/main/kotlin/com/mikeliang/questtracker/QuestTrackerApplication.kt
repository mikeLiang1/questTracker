package com.mikeliang.questtracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mikeliang.questtracker.health.HealthSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QuestTrackerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler

    /** WorkManager is initialized on demand with this so @HiltWorker workers inject. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        healthSyncScheduler.ensurePeriodicSync()
    }
}
