package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.FakeClock
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JournalCompletionTest {

    // 2026-07-16 09:00 UTC, a Thursday.
    private val clock = FakeClock(Instant.parse("2026-07-16T09:00:00Z"))
    private val engine = QuestEngine(clock)

    @Test
    fun `completes every active journal-linked quest and nothing else`() {
        val journal = recurringQuest(id = "journal", attribute = Attribute.Mind, journalLinked = true)
        val gratitude = recurringQuest(id = "gratitude", cadence = Cadence.Weekly, journalLinked = true)
        val gym = recurringQuest(id = "gym")
        val retired = recurringQuest(id = "old", journalLinked = true, status = QuestStatus.Retired)
        val side = sideQuest(id = "side")
        val quests = listOf(journal, gratitude, gym, retired, side)

        val result = engine.completeFromJournalEntry(quests, emptyList())

        assertEquals(setOf("journal", "gratitude"), result.records.map { it.questId.value }.toSet())
        assertNotNull(result.feedback)
    }

    @Test
    fun `records carry Manual source and frozen accrual context`() {
        val journal = recurringQuest(id = "journal", attribute = Attribute.Mind, journalLinked = true)

        val record = engine.completeFromJournalEntry(listOf(journal), emptyList()).records.single()

        assertEquals(CompletionSource.Manual, record.source)
        assertEquals(date("2026-07-16"), record.periodStart)
        assertEquals(Attribute.Mind, record.attribute)
        assertEquals(AccrualRules.basePoints(Cadence.Daily), record.basePoints)
        assertEquals(clock.now(), record.completedAt)
    }

    @Test
    fun `a second entry the same period banks nothing - calmly`() {
        val journal = recurringQuest(id = "journal", journalLinked = true)
        val first = engine.completeFromJournalEntry(listOf(journal), emptyList())

        val second = engine.completeFromJournalEntry(listOf(journal), first.records)

        assertTrue(second.records.isEmpty())
        assertNull(second.feedback)
    }

    @Test
    fun `a weekly linked quest already banked this ISO week is skipped`() {
        val weekly = recurringQuest(id = "weekly", cadence = Cadence.Weekly, journalLinked = true)
        // Monday of the current week — same period as Thursday the 16th.
        val history = completions(weekly, date("2026-07-13"))

        val result = engine.completeFromJournalEntry(listOf(weekly), history)

        assertTrue(result.records.isEmpty())
    }

    @Test
    fun `feedback is computed with every record from this save visible`() {
        // Two linked dailies are the whole board; one save clears it, so the
        // feedback path must see both records or done-for-today would be wrong.
        val journal = recurringQuest(id = "journal", journalLinked = true)
        val gratitude = recurringQuest(id = "gratitude", journalLinked = true)

        val result = engine.completeFromJournalEntry(listOf(journal, gratitude), emptyList())

        assertEquals(2, result.records.size)
        // Both records credit today; the board is now fully banked.
        val board = engine.todayBoard(listOf(journal, gratitude), result.records)
        assertTrue(board.doneForToday)
    }

    @Test
    fun `no journal-linked quests means an empty, feedback-free result`() {
        val result = engine.completeFromJournalEntry(listOf(recurringQuest()), emptyList())

        assertTrue(result.records.isEmpty())
        assertNull(result.feedback)
    }
}
