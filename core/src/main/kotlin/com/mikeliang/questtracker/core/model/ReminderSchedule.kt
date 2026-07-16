package com.mikeliang.questtracker.core.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A user-chosen reminder, owned by its quest. The engine computes next-due occurrences
 * as pure data ([com.mikeliang.questtracker.core.engine.nextReminderAfter]); OS
 * scheduling lives in :app. Notification content is only ever the quest's name at this
 * time — never guilt copy, and never a reminder the user didn't schedule.
 */
sealed interface ReminderSchedule {

    /** Fires at [time] on each of [days]; the usual shape for recurring quests. */
    data class Recurring(
        val time: LocalTime,
        val days: Set<DayOfWeek>,
    ) : ReminderSchedule

    /** Fires once at [at] (interpreted in the user's current zone); for side quests. */
    data class OneShot(val at: LocalDateTime) : ReminderSchedule
}
