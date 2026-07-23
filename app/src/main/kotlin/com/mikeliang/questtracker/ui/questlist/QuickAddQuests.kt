package com.mikeliang.questtracker.ui.questlist

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * Shapes a quick-add event into a new [Quest], or null for a blank title. Input
 * shaping, not engine logic — shared by the Today screen's quick-add sheet and the
 * reflection flow's add-a-quest path, so both build identical quests.
 */
fun questFromQuickAdd(event: QuestListEvent, clock: Clock): Quest? = when (event) {
    is QuestListEvent.AddSideQuest -> {
        val title = event.title.trim()
        if (title.isEmpty()) null else Quest(
            id = newQuestId(),
            title = title,
            kind = QuestKind.SideQuest,
            createdAt = clock.now(),
            reminder = event.reminderTime?.let {
                ReminderSchedule.OneShot(at = nextOccurrenceOf(it, clock))
            },
        )
    }

    is QuestListEvent.AddRecurringQuest -> {
        val title = event.title.trim()
        if (title.isEmpty()) null else Quest(
            id = newQuestId(),
            title = title,
            // Quick-add captures Maintenance quests only; progression targets
            // are added from the quest detail screen, not a 5-second sheet.
            kind = QuestKind.Recurring(
                cadence = event.cadence,
                type = QuestType.Maintenance,
                attribute = event.attribute,
                journalLinked = event.journalLinked,
            ),
            createdAt = clock.now(),
            reminder = event.reminderTime?.let { reminderScheduleFor(event.cadence, it, clock) },
        )
    }

    else -> null
}

private fun newQuestId(): QuestId = QuestId(UUID.randomUUID().toString())

/**
 * Quick-added recurring reminders: dailies nudge every day; weeklies and monthlies
 * nudge once a week on the day the quest was created — the sheet has no day picker,
 * and a weekly nudge is the least-surprising default. Editable from the quest's
 * detail screen.
 */
private fun reminderScheduleFor(cadence: Cadence, time: LocalTime, clock: Clock): ReminderSchedule =
    ReminderSchedule.Recurring(
        time = time,
        days = when (cadence) {
            Cadence.Daily -> DayOfWeek.entries.toSet()
            Cadence.Weekly, Cadence.Monthly -> setOf(clock.today().dayOfWeek)
        },
    )

/**
 * A quick-added reminder means "next time it's this o'clock" — today if the time
 * is still ahead, tomorrow otherwise.
 */
private fun nextOccurrenceOf(time: LocalTime, clock: Clock): LocalDateTime {
    val now = LocalDateTime.ofInstant(clock.now(), clock.zone())
    val todayAt = now.toLocalDate().atTime(time)
    return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
}
