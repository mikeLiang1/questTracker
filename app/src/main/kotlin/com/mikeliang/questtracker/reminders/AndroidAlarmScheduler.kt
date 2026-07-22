package com.mikeliang.questtracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.reminders.ReminderContract.ACTION_FIRE
import com.mikeliang.questtracker.reminders.ReminderContract.EXTRA_QUEST_ID
import com.mikeliang.questtracker.reminders.ReminderContract.fireRequestCode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-backed [AlarmScheduler]. Uses an exact, doze-tolerant alarm when the OS
 * permits it and degrades to an inexact one otherwise — a late reminder still lands in
 * the same period, so nothing about the gains logic is affected. Each quest owns a
 * single pending alarm keyed by [fireRequestCode]; scheduling again replaces it.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: ReminderPermissions,
) : AlarmScheduler {

    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService()) {
        "AlarmManager unavailable"
    }

    override fun schedule(questId: QuestId, at: Instant) {
        // FLAG_UPDATE_CURRENT (no FLAG_NO_CREATE) always returns a live PendingIntent.
        val pendingIntent = requireNotNull(firePendingIntent(questId, PendingIntent.FLAG_UPDATE_CURRENT))
        val triggerAtMillis = at.toEpochMilli()
        // canScheduleExactAlarms can race with revocation, so guard the exact call and
        // fall back rather than let a SecurityException escape.
        val scheduledExact = permissions.canScheduleExactAlarms() &&
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }.isSuccess
        if (!scheduledExact) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(questId: QuestId) {
        val pendingIntent = firePendingIntent(questId, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun firePendingIntent(questId: QuestId, extraFlags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_QUEST_ID, questId.value)
        return PendingIntent.getBroadcast(
            context,
            fireRequestCode(questId),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
