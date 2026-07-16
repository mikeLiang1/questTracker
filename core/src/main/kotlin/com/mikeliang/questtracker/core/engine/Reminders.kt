package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.ReminderSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** How far ahead to search for a recurring occurrence before concluding none exists. */
private const val SEARCH_HORIZON_DAYS = 400

/** A concrete reminder occurrence, ready for :app to hand to the OS scheduler. */
data class DueReminder(val questId: QuestId, val at: Instant)

/**
 * The next moment [quest]'s reminder should fire strictly after [from], or null if
 * it never should. Pure data — OS scheduling lives in :app.
 *
 * Suppression rules (the no-nag law): retired quests never fire; occurrences whose
 * period is already completed are skipped (the scan continues into the next period);
 * a completed side quest's one-shot never fires. Local times that fall in a DST gap
 * shift forward per java.time semantics.
 */
fun nextReminderAfter(
    quest: Quest,
    completions: List<CompletionRecord>,
    from: ZonedDateTime,
): Instant? {
    if (quest.status != QuestStatus.Active) return null
    val schedule = quest.reminder ?: return null
    val questCompletions = completions.filter { it.questId == quest.id }

    fun completedForPeriodOf(date: LocalDate): Boolean = when (val kind = quest.kind) {
        is QuestKind.Recurring -> {
            val start = periodStartFor(date, kind.cadence)
            questCompletions.any { periodStartFor(it.periodStart, kind.cadence) == start }
        }
        QuestKind.SideQuest -> questCompletions.isNotEmpty()
    }

    return when (schedule) {
        is ReminderSchedule.OneShot -> {
            val at = ZonedDateTime.of(schedule.at, from.zone)
            if (at.isAfter(from) && !completedForPeriodOf(at.toLocalDate())) at.toInstant() else null
        }

        is ReminderSchedule.Recurring -> {
            if (schedule.days.isEmpty()) return null
            var date = from.toLocalDate()
            repeat(SEARCH_HORIZON_DAYS) {
                if (date.dayOfWeek in schedule.days) {
                    val at = ZonedDateTime.of(date, schedule.time, from.zone)
                    if (at.isAfter(from) && !completedForPeriodOf(date)) return at.toInstant()
                }
                date = date.plusDays(1)
            }
            null
        }
    }
}

/** Next-due reminders across [quests], soonest first. */
fun dueRemindersAfter(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    from: ZonedDateTime,
): List<DueReminder> = quests
    .mapNotNull { quest ->
        nextReminderAfter(quest, completions, from)?.let { DueReminder(quest.id, it) }
    }
    .sortedBy { it.at }
