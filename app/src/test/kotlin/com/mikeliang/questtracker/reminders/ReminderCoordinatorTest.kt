package com.mikeliang.questtracker.reminders

import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.ui.questlist.FakeQuestRepository
import com.mikeliang.questtracker.ui.questlist.FixedClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The coordinator is where the no-nag law meets the OS. These tests pin the three
 * behaviours the phase calls out — schedule at the right instant, cancel on completion,
 * reschedule wholesale (the boot/time-change path) — plus the suppression rules it
 * inherits from :core, all against a recording fake in place of AlarmManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {

    // FixedClock default: 2026-07-17T10:00Z, a Friday, UTC.
    private val clock = FixedClock()
    private val repository = FakeQuestRepository()
    private val scheduler = RecordingAlarmScheduler()

    private fun coordinator() = ReminderCoordinator(repository, scheduler, QuestEngine(clock), clock)

    private fun dailyQuest(
        id: String = "daily-1",
        at: LocalTime,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    ) = Quest(
        id = QuestId(id),
        title = "Stretch",
        kind = QuestKind.Recurring(Cadence.Daily, QuestType.Maintenance, Attribute.Body),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        reminder = ReminderSchedule.Recurring(at, days),
    )

    private fun sideQuest(id: String = "side-1", at: LocalDateTime) = Quest(
        id = QuestId(id),
        title = "Call plumber",
        kind = QuestKind.SideQuest,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        reminder = ReminderSchedule.OneShot(at),
    )

    @Test
    fun `schedules a daily reminder at the next occurrence after now`() = runTest {
        // 07:00 has passed today (now is 10:00) → the next fire is tomorrow at 07:00.
        repository.seed(dailyQuest(at = LocalTime.of(7, 0)))

        coordinator().rescheduleAll()

        assertEquals(Instant.parse("2026-07-18T07:00:00Z"), scheduler.scheduled[QuestId("daily-1")])
    }

    @Test
    fun `schedules today when the reminder time is still ahead`() = runTest {
        repository.seed(dailyQuest(at = LocalTime.of(19, 0)))

        coordinator().rescheduleAll()

        assertEquals(Instant.parse("2026-07-17T19:00:00Z"), scheduler.scheduled[QuestId("daily-1")])
    }

    @Test
    fun `a quest with no reminder is cancelled, never scheduled`() = runTest {
        repository.seed(
            dailyQuest(at = LocalTime.of(19, 0)).copy(id = QuestId("no-reminder"), reminder = null),
        )

        coordinator().rescheduleAll()

        assertNull(scheduler.scheduled[QuestId("no-reminder")])
        assertTrue(QuestId("no-reminder") in scheduler.cancelled)
    }

    @Test
    fun `an already-completed period is skipped to the next period`() = runTest {
        repository.seed(dailyQuest(at = LocalTime.of(19, 0)))
        // Today's period is already banked → today's 19:00 nudge is suppressed.
        repository.recordCompletion(
            CompletionRecord(
                questId = QuestId("daily-1"),
                completedAt = Instant.parse("2026-07-17T09:00:00Z"),
                periodStart = LocalDate.of(2026, 7, 17),
                source = CompletionSource.Manual,
            ),
        )

        coordinator().rescheduleAll()

        assertEquals(Instant.parse("2026-07-18T19:00:00Z"), scheduler.scheduled[QuestId("daily-1")])
    }

    @Test
    fun `rescheduleAll schedules every quest - the boot and time-change path`() = runTest {
        repository.seed(
            dailyQuest(id = "morning", at = LocalTime.of(7, 0)),
            dailyQuest(id = "evening", at = LocalTime.of(19, 0)),
        )

        coordinator().rescheduleAll()

        assertEquals(Instant.parse("2026-07-18T07:00:00Z"), scheduler.scheduled[QuestId("morning")])
        assertEquals(Instant.parse("2026-07-17T19:00:00Z"), scheduler.scheduled[QuestId("evening")])
    }

    @Test
    fun `completing a side quest via the notification banks it and cancels its alarm`() = runTest {
        val side = sideQuest(at = LocalDateTime.of(2026, 7, 17, 19, 0))
        repository.seed(side)
        val coordinator = coordinator()
        coordinator.rescheduleAll()
        assertTrue(QuestId("side-1") in scheduler.scheduled.keys)

        coordinator.completeFromNotification(QuestId("side-1"))

        // The completion is banked, and a completed one-shot never fires again.
        assertEquals(1, repository.recordedCompletions.size)
        assertNull(scheduler.scheduled[QuestId("side-1")])
        assertTrue(QuestId("side-1") in scheduler.cancelled)
    }

    @Test
    fun `completing a daily via the notification advances the alarm to the next period`() = runTest {
        repository.seed(dailyQuest(at = LocalTime.of(19, 0)))
        val coordinator = coordinator()

        coordinator.completeFromNotification(QuestId("daily-1"))

        assertEquals(1, repository.recordedCompletions.size)
        // Today is done, but the daily still fires tomorrow.
        assertEquals(Instant.parse("2026-07-18T19:00:00Z"), scheduler.scheduled[QuestId("daily-1")])
    }

    @Test
    fun `keepInSync reschedules automatically when a completion is recorded`() = runTest {
        repository.seed(dailyQuest(at = LocalTime.of(19, 0)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator().keepInSync()
        }

        // Initial sync scheduled today's 19:00 nudge.
        assertEquals(Instant.parse("2026-07-17T19:00:00Z"), scheduler.scheduled[QuestId("daily-1")])

        repository.recordCompletion(
            CompletionRecord(
                questId = QuestId("daily-1"),
                completedAt = Instant.parse("2026-07-17T12:00:00Z"),
                periodStart = LocalDate.of(2026, 7, 17),
                source = CompletionSource.Manual,
            ),
        )

        // The collector re-synced: today's nudge gives way to tomorrow's.
        assertEquals(Instant.parse("2026-07-18T19:00:00Z"), scheduler.scheduled[QuestId("daily-1")])
        assertFalse(repository.recordedCompletions.isEmpty())
    }
}

/** Records the alarms the coordinator sets and drops, standing in for AlarmManager. */
private class RecordingAlarmScheduler : AlarmScheduler {
    val scheduled = linkedMapOf<QuestId, Instant>()
    val cancelled = mutableListOf<QuestId>()

    override fun schedule(questId: QuestId, at: Instant) {
        scheduled[questId] = at
    }

    override fun cancel(questId: QuestId) {
        scheduled.remove(questId)
        cancelled += questId
    }
}
