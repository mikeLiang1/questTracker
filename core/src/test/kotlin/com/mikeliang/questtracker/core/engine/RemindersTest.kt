package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RemindersTest {

    private val everyDayAt7 = ReminderSchedule.Recurring(LocalTime.of(7, 0), DayOfWeek.entries.toSet())

    /** Thursday 2026-07-16 at [hour]:00 UTC. */
    private fun from(hour: Int): ZonedDateTime =
        ZonedDateTime.of(date("2026-07-16"), LocalTime.of(hour, 0), ZoneOffset.UTC)

    @Test
    fun `fires later today if the time is still ahead`() {
        val quest = recurringQuest(reminder = everyDayAt7)

        val next = nextReminderAfter(quest, emptyList(), from(6))

        assertEquals(Instant.parse("2026-07-16T07:00:00Z"), next)
    }

    @Test
    fun `rolls to tomorrow once today's time has passed`() {
        val quest = recurringQuest(reminder = everyDayAt7)

        val next = nextReminderAfter(quest, emptyList(), from(8))

        assertEquals(Instant.parse("2026-07-17T07:00:00Z"), next)
    }

    @Test
    fun `only fires on the chosen days`() {
        val monOnly = ReminderSchedule.Recurring(LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY))
        val quest = recurringQuest(reminder = monOnly)

        val next = nextReminderAfter(quest, emptyList(), from(6))

        assertEquals(Instant.parse("2026-07-20T07:00:00Z"), next)
    }

    @Test
    fun `a completed period suppresses its reminder`() {
        val quest = recurringQuest(reminder = everyDayAt7)
        val history = completions(quest, date("2026-07-16"))

        val next = nextReminderAfter(quest, history, from(6))

        assertEquals(Instant.parse("2026-07-17T07:00:00Z"), next)
    }

    @Test
    fun `a completed weekly period suppresses the whole week`() {
        val monAndThu = ReminderSchedule.Recurring(LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val quest = recurringQuest(cadence = Cadence.Weekly, reminder = monAndThu)
        val history = completions(quest, date("2026-07-14")) // this ISO week is done

        val next = nextReminderAfter(quest, history, from(6))

        assertEquals(Instant.parse("2026-07-20T07:00:00Z"), next) // next Monday, new period
    }

    @Test
    fun `one-shot fires once and only in the future`() {
        val at = LocalDateTime.of(2026, 7, 17, 18, 30)
        val quest = sideQuest(reminder = ReminderSchedule.OneShot(at))

        assertEquals(Instant.parse("2026-07-17T18:30:00Z"), nextReminderAfter(quest, emptyList(), from(6)))

        val past = sideQuest(reminder = ReminderSchedule.OneShot(LocalDateTime.of(2026, 7, 15, 18, 30)))
        assertNull(nextReminderAfter(past, emptyList(), from(6)))
    }

    @Test
    fun `a completed side quest never pings`() {
        val at = LocalDateTime.of(2026, 7, 17, 18, 30)
        val quest = sideQuest(reminder = ReminderSchedule.OneShot(at))
        val history = listOf(completion(quest, date("2026-07-16")))

        assertNull(nextReminderAfter(quest, history, from(6)))
    }

    @Test
    fun `retired quests and empty schedules never fire`() {
        val retired = recurringQuest(status = QuestStatus.Retired, reminder = everyDayAt7)
        val noDays = recurringQuest(reminder = ReminderSchedule.Recurring(LocalTime.of(7, 0), emptySet()))
        val noReminder = recurringQuest()

        assertNull(nextReminderAfter(retired, emptyList(), from(6)))
        assertNull(nextReminderAfter(noDays, emptyList(), from(6)))
        assertNull(nextReminderAfter(noReminder, emptyList(), from(6)))
    }

    @Test
    fun `a DST-gap time shifts forward instead of vanishing`() {
        // Sydney DST starts 2026-10-04: 02:00 → 03:00, so 02:30 doesn't exist that day.
        val sydney = ZoneId.of("Australia/Sydney")
        val schedule = ReminderSchedule.Recurring(LocalTime.of(2, 30), setOf(DayOfWeek.SUNDAY))
        val quest = recurringQuest(reminder = schedule)
        val from = ZonedDateTime.of(date("2026-10-03"), LocalTime.NOON, sydney)

        val next = nextReminderAfter(quest, emptyList(), from)

        val local = ZonedDateTime.ofInstant(next!!, sydney)
        assertEquals(date("2026-10-04"), local.toLocalDate())
        assertEquals(LocalTime.of(3, 30), local.toLocalTime())
    }

    @Test
    fun `dueReminders lists soonest first`() {
        val morning = recurringQuest(id = "a", reminder = everyDayAt7)
        val evening = recurringQuest(
            id = "b",
            title = "Read",
            reminder = ReminderSchedule.Recurring(LocalTime.of(21, 0), DayOfWeek.entries.toSet()),
        )

        val due = dueRemindersAfter(listOf(evening, morning), emptyList(), from(6))

        assertEquals(listOf("a", "b"), due.map { it.questId.value })
    }
}
