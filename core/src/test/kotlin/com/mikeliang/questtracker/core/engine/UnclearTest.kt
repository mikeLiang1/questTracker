package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.FakeClock
import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnclearTest {

    private val today = date("2026-07-16") // Thursday
    private val zone = ZoneOffset.UTC

    @Test
    fun `a manual completion banked today is uncleared`() {
        val daily = recurringQuest()
        val record = completion(daily, today)

        val outcome = unclearQuest(daily, listOf(record), today, zone)

        assertEquals(UnclearOutcome.Uncleared(record), outcome)
    }

    @Test
    fun `a completion banked before today stays banked`() {
        // Weekly cleared Monday; un-clearing on Thursday must not touch it.
        val weekly = recurringQuest(cadence = Cadence.Weekly)
        val record = completion(weekly, date("2026-07-13"))

        val outcome = unclearQuest(weekly, listOf(record), today, zone)

        assertEquals(UnclearOutcome.NotUndoable, outcome)
    }

    @Test
    fun `auto-tracked completions are never uncleared`() {
        val daily = recurringQuest()
        val record = completion(daily, today, source = CompletionSource.AutoTracked)

        val outcome = unclearQuest(daily, listOf(record), today, zone)

        assertEquals(UnclearOutcome.NotUndoable, outcome)
    }

    @Test
    fun `nothing to unclear when the current period has no completion`() {
        val daily = recurringQuest()
        val stale = completion(daily, today.minusDays(1))

        val outcome = unclearQuest(daily, listOf(stale), today, zone)

        assertEquals(UnclearOutcome.NotUndoable, outcome)
    }

    @Test
    fun `a side quest ticked today is uncleared`() {
        val side = sideQuest()
        val record = completion(side, today)

        val outcome = unclearQuest(side, listOf(record), today, zone)

        assertEquals(UnclearOutcome.Uncleared(record), outcome)
    }

    @Test
    fun `only the target quest's records are considered`() {
        val daily = recurringQuest(id = "mine")
        val other = recurringQuest(id = "other")
        val history = completions(other, today)

        val outcome = unclearQuest(daily, history, today, zone)

        assertEquals(UnclearOutcome.NotUndoable, outcome)
    }

    @Test
    fun `same-day is judged in the user's current zone - not UTC`() {
        // Banked at 23:00 UTC on the 15th = 11:00 on the 16th at UTC+12.
        val daily = recurringQuest()
        val record = completion(
            daily, today,
            completedAt = Instant.parse("2026-07-15T23:00:00Z"),
        )

        val utc = unclearQuest(daily, listOf(record), today, ZoneOffset.UTC)
        val auckland = unclearQuest(daily, listOf(record), today, ZoneOffset.ofHours(12))

        assertEquals(UnclearOutcome.NotUndoable, utc)
        assertEquals(UnclearOutcome.Uncleared(record), auckland)
    }

    @Test
    fun `engine unclear uses the clock's today and zone`() {
        val clock = FakeClock(instant = Instant.parse("2026-07-16T10:00:00Z"))
        val engine = QuestEngine(clock)
        val daily = recurringQuest()
        val record = completion(daily, today)

        val outcome = engine.unclear(daily, listOf(record))

        assertTrue(outcome is UnclearOutcome.Uncleared)
    }
}
