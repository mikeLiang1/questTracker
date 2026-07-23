package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.progressionQuest
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonthlyReflectionTest {

    private val zone = ZoneOffset.UTC
    private val today = date("2026-07-17") // reviewed month: June 2026

    // --- buildReflectionSummary ---------------------------------------------

    @Test
    fun `reviews the previous calendar month`() {
        val summary = buildReflectionSummary(emptyList(), emptyList(), today, zone)

        assertEquals(date("2026-06-01"), summary.month.start)
        assertEquals(date("2026-06-30"), summary.month.endInclusive)
    }

    @Test
    fun `counts only completions that happened inside the month`() {
        val quest = recurringQuest()
        val records = completions(
            quest,
            date("2026-05-30"), // before
            date("2026-06-01"),
            date("2026-06-15"),
            date("2026-07-02"), // after
        )

        val summary = buildReflectionSummary(listOf(quest), records, today, zone)

        assertEquals(2, summary.quests.single().completionsInMonth)
    }

    @Test
    fun `consistency is evaluated as of month end, not today`() {
        val quest = recurringQuest(createdAt = Instant.parse("2026-06-01T00:00:00Z"))
        // Every day of June completed, nothing since — the 17 elapsed July days must
        // not drag the reviewed month's score down.
        val june = (1..30).map { date("2026-06-%02d".format(it)) }
        val records = completions(quest, *june.toTypedArray())

        val summary = buildReflectionSummary(listOf(quest), records, today, zone)

        assertEquals(1.0, summary.quests.single().consistency.rate)
    }

    @Test
    fun `only active recurring quests that existed during the month get rows`() {
        val active = recurringQuest(id = "active")
        val retired = recurringQuest(id = "retired", status = QuestStatus.Retired)
        val bornThisMonth = recurringQuest(
            id = "new",
            createdAt = Instant.parse("2026-07-05T00:00:00Z"),
        )
        val side = sideQuest(id = "side")

        val summary = buildReflectionSummary(
            listOf(active, retired, bornThisMonth, side), emptyList(), today, zone,
        )

        assertEquals(listOf("active"), summary.quests.map { it.quest.id.value })
    }

    // --- escalation detection -----------------------------------------------

    @Test
    fun `escalation mid-month shows as two levels among the month's records`() {
        val quest = progressionQuest(escalationLevel = 1)
        val records = listOf(
            completion(quest, date("2026-06-03"), escalationLevel = 0),
            completion(quest, date("2026-06-20"), escalationLevel = 1),
        )

        val summary = buildReflectionSummary(listOf(quest), records, today, zone)

        assertTrue(summary.quests.single().escalatedInMonth)
    }

    @Test
    fun `escalation between months shows as a rise past the prior level`() {
        val quest = progressionQuest(escalationLevel = 1)
        val records = listOf(
            completion(quest, date("2026-05-28"), escalationLevel = 0),
            completion(quest, date("2026-06-10"), escalationLevel = 1),
        )

        val summary = buildReflectionSummary(listOf(quest), records, today, zone)

        assertTrue(summary.quests.single().escalatedInMonth)
    }

    @Test
    fun `a steady level is not an escalation`() {
        val quest = progressionQuest()
        val records = listOf(
            completion(quest, date("2026-05-28"), escalationLevel = 0),
            completion(quest, date("2026-06-10"), escalationLevel = 0),
            completion(quest, date("2026-06-11"), escalationLevel = 0),
        )

        val summary = buildReflectionSummary(listOf(quest), records, today, zone)

        assertFalse(summary.quests.single().escalatedInMonth)
    }

    // --- attribute gains ----------------------------------------------------

    @Test
    fun `attribute gains sum only the month's records, all attributes present`() {
        val body = recurringQuest(id = "body", attribute = Attribute.Body)
        val mind = recurringQuest(id = "mind", attribute = Attribute.Mind, cadence = Cadence.Weekly)
        val records =
            completions(body, date("2026-05-30"), date("2026-06-01"), date("2026-06-02")) +
                completions(mind, date("2026-06-08")) // one week, 3.0 base

        val summary = buildReflectionSummary(listOf(body, mind), records, today, zone)

        assertEquals(2.0, summary.attributeGains[Attribute.Body])
        assertEquals(3.0, summary.attributeGains[Attribute.Mind])
        assertEquals(0.0, summary.attributeGains[Attribute.Social])
        assertEquals(0.0, summary.attributeGains[Attribute.Discipline])
    }

    @Test
    fun `gains respect diminishing returns earned across the level's full history`() {
        val quest = progressionQuest()
        // 14 pre-June completions at level 0, then two in June: the 15th still earns
        // full base, the 16th is diminished — 1.0 + 0.5 inside the month.
        val may = (1..14).map { date("2026-05-%02d".format(it)) }
        val records = completions(quest, *may.toTypedArray()) +
            completions(quest, date("2026-06-01"), date("2026-06-02"))

        val summary = buildReflectionSummary(listOf(quest), records, today, zone)

        assertEquals(1.5, summary.attributeGains[Attribute.Body])
    }

    @Test
    fun `a quest retired since still contributes the gains it banked in the month`() {
        val retired = recurringQuest(id = "retired", status = QuestStatus.Retired)
        val records = completions(retired, date("2026-06-05"))

        val summary = buildReflectionSummary(listOf(retired), records, today, zone)

        assertTrue(summary.quests.isEmpty())
        assertEquals(1.0, summary.attributeGains[Attribute.Body])
    }

    // --- barely moved -------------------------------------------------------

    @Test
    fun `the quietest quest is the one with the lowest consistency`() {
        val steady = recurringQuest(id = "steady", createdAt = Instant.parse("2026-06-01T00:00:00Z"))
        val quiet = recurringQuest(id = "quiet", createdAt = Instant.parse("2026-06-01T00:00:00Z"))
        val june = (1..30).map { date("2026-06-%02d".format(it)) }
        val records = completions(steady, *june.toTypedArray()) +
            completions(quiet, date("2026-06-01"))

        val summary = buildReflectionSummary(listOf(steady, quiet), records, today, zone)

        assertEquals("quiet", summary.barelyMovedQuestId?.value)
    }

    @Test
    fun `a lone quest never gets the callout`() {
        val quest = recurringQuest()

        val summary = buildReflectionSummary(listOf(quest), emptyList(), today, zone)

        assertNull(summary.barelyMovedQuestId)
    }

    @Test
    fun `a clean sweep never gets the callout`() {
        val a = recurringQuest(id = "a", createdAt = Instant.parse("2026-06-01T00:00:00Z"))
        val b = recurringQuest(id = "b", createdAt = Instant.parse("2026-06-01T00:00:00Z"))
        val june = (1..30).map { date("2026-06-%02d".format(it)) }
        val records = completions(a, *june.toTypedArray()) + completions(b, *june.toTypedArray())

        val summary = buildReflectionSummary(listOf(a, b), records, today, zone)

        assertNull(summary.barelyMovedQuestId)
    }

    // --- isReflectionDue ----------------------------------------------------

    @Test
    fun `due once history predates the current month and the month is unhandled`() {
        val quest = recurringQuest()
        val records = completions(quest, date("2026-06-10"))

        assertTrue(isReflectionDue(records, today, lastHandledMonth = null))
        assertTrue(isReflectionDue(records, today, lastHandledMonth = YearMonth.of(2026, 6)))
    }

    @Test
    fun `not due with no history at all`() {
        assertFalse(isReflectionDue(emptyList(), today, lastHandledMonth = null))
    }

    @Test
    fun `not due when all history is from the current month`() {
        val quest = recurringQuest()
        val records = completions(quest, date("2026-07-02"), date("2026-07-10"))

        assertFalse(isReflectionDue(records, today, lastHandledMonth = null))
    }

    @Test
    fun `not due again once the current month is handled — skipped or completed alike`() {
        val quest = recurringQuest()
        val records = completions(quest, date("2026-06-10"))

        assertFalse(isReflectionDue(records, today, lastHandledMonth = YearMonth.of(2026, 7)))
    }
}
