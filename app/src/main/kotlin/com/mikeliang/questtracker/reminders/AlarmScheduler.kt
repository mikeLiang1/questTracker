package com.mikeliang.questtracker.reminders

import com.mikeliang.questtracker.core.model.QuestId
import java.time.Instant

/**
 * The OS-scheduling seam. [ReminderCoordinator] decides *when* a quest's reminder
 * should fire (via :core's pure next-due computation) and hands the concrete instant
 * here; the Android implementation is the only part that touches AlarmManager, which
 * keeps the coordinator unit-testable with a fake.
 */
interface AlarmScheduler {

    /** Schedule (or replace) the single pending alarm for [questId] to fire at [at]. */
    fun schedule(questId: QuestId, at: Instant)

    /** Drop any pending alarm for [questId]. A no-op if none is scheduled. */
    fun cancel(questId: QuestId)
}
