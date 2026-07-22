package com.mikeliang.questtracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.repository.QuestRepository
import com.mikeliang.questtracker.reminders.ReminderContract.ACTION_COMPLETE
import com.mikeliang.questtracker.reminders.ReminderContract.EXTRA_QUEST_ID
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives a fired reminder alarm and its "Complete" action. On a fire it posts the
 * notification and advances the quest's alarm to the next occurrence; on Complete it
 * banks the completion and clears the notification. Work runs in a [goAsync] window so
 * the short repository/DB reads finish before the process may be reclaimed.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: ReminderCoordinator
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var repository: QuestRepository

    override fun onReceive(context: Context, intent: Intent) {
        val questId = intent.getStringExtra(EXTRA_QUEST_ID)?.let(::QuestId) ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                if (intent.action == ACTION_COMPLETE) {
                    notifier.cancel(questId)
                    coordinator.completeFromNotification(questId)
                } else {
                    // The alarm fired. :core already suppressed completed periods when
                    // scheduling, and completing in-app cancels the alarm, so a quest
                    // reaching here is genuinely still due for its period.
                    repository.getQuest(questId)?.let(notifier::notify)
                    coordinator.rescheduleOne(questId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
