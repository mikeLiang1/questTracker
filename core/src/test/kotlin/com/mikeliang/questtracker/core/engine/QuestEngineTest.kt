package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.FakeClock
import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.progressionQuest
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QuestEngineTest {

    // 2026-07-16 09:00 UTC, a Thursday.
    private val clock = FakeClock(Instant.parse("2026-07-16T09:00:00Z"))
    private val engine = QuestEngine(clock)

    private fun completed(outcome: CompletionOutcome): CompletionOutcome.Completed =
        assertInstanceOf(CompletionOutcome.Completed::class.java, outcome)

    @Test
    fun `completing a daily quest credits today and carries the source`() {
        val quest = recurringQuest()

        val outcome = completed(engine.complete(quest, listOf(quest), emptyList(), CompletionSource.Manual))

        assertEquals(quest.id, outcome.record.questId)
        assertEquals(date("2026-07-16"), outcome.record.periodStart)
        assertEquals(CompletionSource.Manual, outcome.record.source)
        assertEquals(clock.now(), outcome.record.completedAt)
        assertEquals(null, outcome.record.escalationLevel)
    }

    @Test
    fun `the credited period follows the user's zone - late UTC is tomorrow in Sydney`() {
        clock.instant = Instant.parse("2026-07-16T15:30:00Z")
        clock.zoneId = ZoneId.of("Australia/Sydney")
        val quest = recurringQuest()

        val outcome = completed(engine.complete(quest, listOf(quest), emptyList(), CompletionSource.AutoTracked))

        assertEquals(date("2026-07-17"), outcome.record.periodStart)
    }

    @Test
    fun `a weekly quest credits the ISO week's Monday`() {
        val quest = recurringQuest(cadence = Cadence.Weekly)

        val outcome = completed(engine.complete(quest, listOf(quest), emptyList(), CompletionSource.Manual))

        assertEquals(date("2026-07-13"), outcome.record.periodStart)
    }

    @Test
    fun `completing twice in one period is a calm no-op`() {
        val quest = recurringQuest()
        val history = completions(quest, date("2026-07-16"))

        val outcome = engine.complete(quest, listOf(quest), history, CompletionSource.Manual)

        assertEquals(CompletionOutcome.AlreadyCompleted, outcome)
    }

    @Test
    fun `progression completions record the current escalation level`() {
        val quest = progressionQuest(escalationLevel = 2)

        val outcome = completed(engine.complete(quest, listOf(quest), emptyList(), CompletionSource.AutoTracked))

        assertEquals(2, outcome.record.escalationLevel)
    }

    @Test
    fun `retired quests cannot be completed`() {
        val quest = recurringQuest(status = QuestStatus.Retired)

        assertThrows<IllegalArgumentException> {
            engine.complete(quest, listOf(quest), emptyList(), CompletionSource.Manual)
        }
    }

    @Test
    fun `feedback - consecutive periods produce an identity-framed run`() {
        val quest = recurringQuest()
        val history = completions(quest, date("2026-07-13"), date("2026-07-14"), date("2026-07-15"))

        val feedback = completed(engine.complete(quest, listOf(quest), history, CompletionSource.Manual)).feedback

        val run = assertInstanceOf(CompletionFeedback.ConsecutiveRun::class.java, feedback)
        assertEquals(4, run.runLength)
        assertEquals("4th day running — consistent.", run.message)
    }

    @Test
    fun `feedback - weekly runs speak in weeks`() {
        val quest = recurringQuest(cadence = Cadence.Weekly)
        val history = completions(quest, date("2026-06-22"), date("2026-06-29"), date("2026-07-06"))

        val feedback = completed(engine.complete(quest, listOf(quest), history, CompletionSource.Manual)).feedback

        assertEquals("4th week running — consistent.", feedback.message)
    }

    @Test
    fun `feedback - crossing a milestone outranks the run`() {
        val quest = recurringQuest(attribute = Attribute.Body)
        // 4 points banked; the 5th crosses rank 1 despite also being a 5-day run.
        val history = completions(
            quest,
            date("2026-07-12"), date("2026-07-13"), date("2026-07-14"), date("2026-07-15"),
        )

        val feedback = completed(engine.complete(quest, listOf(quest), history, CompletionSource.Manual)).feedback

        val milestone = assertInstanceOf(CompletionFeedback.MilestoneReached::class.java, feedback)
        assertEquals(1, milestone.newRank)
        assertEquals("Awakened", milestone.newTitle)
        assertEquals("New title: Awakened — Body.", milestone.message)
    }

    @Test
    fun `feedback - scattered completions fall back to banked evidence`() {
        val quest = recurringQuest()
        val history = completions(quest, date("2026-07-10"), date("2026-07-12"))

        val feedback = completed(engine.complete(quest, listOf(quest), history, CompletionSource.Manual)).feedback

        val banked = assertInstanceOf(CompletionFeedback.EvidenceBanked::class.java, feedback)
        assertEquals(3, banked.lifetimeCompletions)
        assertEquals("Completion #3 — banked for good.", banked.message)
    }

    @Test
    fun `side quests tick and tally - never attribute feedback`() {
        val done = sideQuest(id = "s1")
        val doing = sideQuest(id = "s2", title = "Renew rego")
        val history = listOf(completion(done, date("2026-07-10")))

        val outcome = completed(engine.complete(doing, listOf(done, doing), history, CompletionSource.Manual))

        val cleared = assertInstanceOf(CompletionFeedback.SideQuestCleared::class.java, outcome.feedback)
        assertEquals(2, cleared.lifetimeSideQuests)
        assertEquals("Side quest cleared — 2nd lifetime.", cleared.message)
        assertEquals(null, outcome.record.escalationLevel)
    }

    @Test
    fun `a completed side quest stays completed`() {
        val quest = sideQuest()
        val history = listOf(completion(quest, date("2026-07-10")))

        assertEquals(
            CompletionOutcome.AlreadyCompleted,
            engine.complete(quest, listOf(quest), history, CompletionSource.Manual),
        )
    }

    @Test
    fun `lifetime completions count periods and cleared side quests`() {
        val daily = recurringQuest()
        val side = sideQuest()
        val history =
            completions(daily, date("2026-07-13"), date("2026-07-14")) +
                completion(daily, date("2026-07-14"), completedAt = Instant.parse("2026-07-14T20:00:00Z")) + // dupe
                completion(side, date("2026-07-10"))

        assertEquals(3, lifetimeCompletionCount(listOf(daily, side), history))
    }

    @ParameterizedTest
    @CsvSource(
        "1, 1st", "2, 2nd", "3, 3rd", "4, 4th",
        "11, 11th", "12, 12th", "13, 13th",
        "21, 21st", "22, 22nd", "23, 23rd", "101, 101st", "111, 111th",
    )
    fun `ordinals read naturally`(n: Int, expected: String) {
        assertEquals(expected, n.toOrdinal())
    }
}
