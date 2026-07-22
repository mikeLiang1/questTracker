package com.mikeliang.questtracker.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.mikeliang.questtracker.MainActivity
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.reminders.ReminderContract.ACTION_COMPLETE
import com.mikeliang.questtracker.reminders.ReminderContract.CHANNEL_ID
import com.mikeliang.questtracker.reminders.ReminderContract.EXTRA_QUEST_ID
import com.mikeliang.questtracker.reminders.ReminderContract.completeRequestCode
import com.mikeliang.questtracker.reminders.ReminderContract.fireRequestCode
import com.mikeliang.questtracker.reminders.ReminderContract.openRequestCode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts the reminder notification. The hard rule made concrete: the content
 * is *only* the quest's title — no body, no streak, no "you haven't". Tapping opens the
 * app; a "Complete" action banks the quest without leaving the notification shade. If
 * the post-notifications permission is absent, this silently does nothing (feature off).
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: ReminderPermissions,
) {

    private val notificationManager: NotificationManager? = context.getSystemService()

    /**
     * Post [quest]'s reminder. The notification id is the quest's fire request code, so
     * a re-fire replaces rather than stacks, and [cancel] can find it.
     */
    fun notify(quest: Quest) {
        if (!permissions.canPostNotifications()) return
        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            // Title only — deliberately no setContentText / no guilt copy of any kind.
            .setContentTitle(quest.title)
            .setContentIntent(openAppIntent(quest.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.checkbox_on_background,
                COMPLETE_ACTION_LABEL,
                completeIntent(quest.id),
            )
            .build()

        NotificationManagerCompat.from(context).notify(fireRequestCode(quest.id), notification)
    }

    /** Remove the posted reminder for [questId] (e.g. after its Complete action). */
    fun cancel(questId: QuestId) {
        NotificationManagerCompat.from(context).cancel(fireRequestCode(questId))
    }

    private fun ensureChannel() {
        val manager = notificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                // Low importance: a quiet at-a-glance nudge, never an interruptive alert.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = CHANNEL_DESCRIPTION },
        )
    }

    private fun openAppIntent(questId: QuestId): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Carried through for the future quest-detail deep link; the list screen
            // simply ignores it today.
            .putExtra(EXTRA_QUEST_ID, questId.value)
        return PendingIntent.getActivity(
            context,
            openRequestCode(questId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun completeIntent(questId: QuestId): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_COMPLETE)
            .putExtra(EXTRA_QUEST_ID, questId.value)
        return PendingIntent.getBroadcast(
            context,
            completeRequestCode(questId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_NAME = "Quest reminders"
        const val CHANNEL_DESCRIPTION = "Nudges at the times you chose, showing only the quest name."
        const val COMPLETE_ACTION_LABEL = "Complete"
    }
}
