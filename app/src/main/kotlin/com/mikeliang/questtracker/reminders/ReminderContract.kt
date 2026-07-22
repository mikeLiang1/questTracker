package com.mikeliang.questtracker.reminders

import com.mikeliang.questtracker.core.model.QuestId

/**
 * The intent vocabulary shared between the alarm scheduler, the notification builder,
 * and the receivers. Kept in one place so a request-code or action string is never
 * spelled two different ways across the pipeline.
 */
internal object ReminderContract {

    /** String value of the [QuestId] an intent refers to. */
    const val EXTRA_QUEST_ID = "com.mikeliang.questtracker.reminders.EXTRA_QUEST_ID"

    /** An alarm fired: show the reminder and schedule the next occurrence. */
    const val ACTION_FIRE = "com.mikeliang.questtracker.reminders.FIRE"

    /** The notification's "Complete" button: bank the completion and dismiss. */
    const val ACTION_COMPLETE = "com.mikeliang.questtracker.reminders.COMPLETE"

    /** The single low-importance channel all quest reminders post to. */
    const val CHANNEL_ID = "quest_reminders"

    // Distinct request codes per purpose so the three PendingIntents for one quest
    // (fire alarm, complete action, content tap) never collide.
    fun fireRequestCode(id: QuestId): Int = id.value.hashCode()
    fun completeRequestCode(id: QuestId): Int = id.value.hashCode() xor 0x5F5F5F5F
    fun openRequestCode(id: QuestId): Int = id.value.hashCode() xor 0x0A0A0A0A
}
