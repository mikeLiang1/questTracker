package com.mikeliang.questtracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets every alarm on reboot, and wall-clock changes move when a local
 * time actually occurs, so all three cases require recomputing the whole schedule from
 * :core. [ReminderCoordinator.rescheduleAll] re-derives each quest's next occurrence in
 * the current zone — the fix for reboot and for timezone travel is the same code.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: ReminderCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                val pendingResult = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                scope.launch {
                    try {
                        coordinator.rescheduleAll()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
