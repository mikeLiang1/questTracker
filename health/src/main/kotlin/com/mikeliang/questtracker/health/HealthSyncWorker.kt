package com.mikeliang.questtracker.health

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Thin WorkManager shell around [HealthReconciler] — all behaviour worth testing
 * lives there. Always reports success: a failed sync is just absence of data, the
 * 15-minute periodic cadence is the retry, and WorkManager backoff/failure states
 * would add machinery to a pass that is already idempotent and quiet.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: HealthReconciler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            reconciler.reconcile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately quiet — never a user-visible failure state.
        }
        return Result.success()
    }
}
