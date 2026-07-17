package com.mikeliang.questtracker.health

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the two sync triggers the Phase 4 policy defines. Both run the same
 * [HealthSyncWorker]; no constraints — reads are local IPC, no network involved.
 */
@Singleton
class HealthSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Idempotent; call on app start. 15 minutes is WorkManager's minimum period. */
    fun ensurePeriodicSync() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HealthSyncWorker>(Duration.ofMinutes(15)).build(),
        )
    }

    /**
     * The on-app-open reconciliation. Not expedited: pre-S expedited work runs as a
     * foreground service whose notification the user never scheduled — against the
     * notification rule — and plain one-time work starts promptly for a foregrounded
     * app anyway.
     */
    fun reconcileNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ON_OPEN_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HealthSyncWorker>().build(),
        )
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "health-sync-periodic"
        const val ON_OPEN_WORK_NAME = "health-sync-on-open"
    }
}
