package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileTest {

    private val today = date("2026-07-17")

    private fun card(summary: ProfileSummary, attribute: Attribute): AttributeCard =
        summary.attributes.first { it.attribute == attribute }

    @Test
    fun `fresh user gets all four cards at rank zero with neutral copy`() {
        val summary = buildProfile(emptyList(), emptyList(), today)

        assertEquals(Attribute.entries.toList(), summary.attributes.map { it.attribute })
        summary.attributes.forEach { card ->
            assertEquals(0, card.rank)
            assertEquals("Unwritten", card.title)
            assertEquals("Awakened", card.nextTitle)
            // Untouched attributes count in daily completions: rank 1 needs 5 points.
            assertEquals(5, card.completionsToNextRank)
            assertEquals(0.0, card.progressToNextRank)
            assertEquals("No quests feed ${card.attribute} yet", card.evidence)
        }
        assertEquals(0, summary.lifetimeCompletions)
        assertTrue(summary.chapters.isEmpty())
    }

    @Test
    fun `attribute card reflects accrued points, rank, and evidence span`() {
        val quest = recurringQuest(id = "read", attribute = Attribute.Mind)
        // 6 daily completions across ~4 weeks → 6 points → rank 1 (threshold 5).
        val history = completions(
            quest,
            date("2026-06-22"), date("2026-06-25"), date("2026-06-29"),
            date("2026-07-03"), date("2026-07-08"), date("2026-07-14"),
        )

        val card = card(buildProfile(listOf(quest), history, today), Attribute.Mind)

        assertEquals(1, card.rank)
        assertEquals("Awakened", card.title)
        assertEquals(6.0, card.points)
        assertEquals(6, card.completions)
        assertEquals("Committed", card.nextTitle)
        // 15 − 6 = 9 points to rank 2, at 1 point per daily completion.
        assertEquals(9, card.completionsToNextRank)
        // (6 − 5) / (15 − 5) = 0.1 of the way from rank 1 to rank 2.
        assertEquals(0.1, card.progressToNextRank, 1e-9)
        // 2026-06-22..2026-07-17 inclusive is 26 days → 4 weeks.
        assertEquals("6 completions over 4 weeks", card.evidence)
    }

    @Test
    fun `completions to next rank uses the fastest active quest feeding the attribute`() {
        val daily = recurringQuest(id = "daily", attribute = Attribute.Body)
        val weekly = recurringQuest(id = "weekly", cadence = Cadence.Weekly, attribute = Attribute.Body)

        val card = card(buildProfile(listOf(daily, weekly), emptyList(), today), Attribute.Body)

        // 5 points to Awakened at 3 points per weekly completion → ⌈5/3⌉ = 2.
        assertEquals(2, card.completionsToNextRank)
    }

    @Test
    fun `retired quests keep their points but stop shaping the next-rank estimate`() {
        val retired = recurringQuest(
            id = "old-weekly", cadence = Cadence.Weekly,
            attribute = Attribute.Body, status = QuestStatus.Retired,
        )
        val history = completions(retired, date("2026-06-01"))

        val card = card(buildProfile(listOf(retired), history, today), Attribute.Body)

        // The 3 banked points are permanent…
        assertEquals(3.0, card.points)
        assertEquals(1, card.completions)
        // …but with no active quest, the estimate falls back to daily completions: ⌈2/1⌉.
        assertEquals(2, card.completionsToNextRank)
    }

    @Test
    fun `attribute with active quests but no history reads as not yet, never as failure`() {
        val quest = recurringQuest(id = "call", attribute = Attribute.Social)

        val card = card(buildProfile(listOf(quest), emptyList(), today), Attribute.Social)

        assertEquals("No completions banked yet", card.evidence)
    }

    @Test
    fun `single completion uses singular copy and a day span`() {
        val quest = recurringQuest(id = "gym", attribute = Attribute.Body)
        val history = completions(quest, today)

        val card = card(buildProfile(listOf(quest), history, today), Attribute.Body)

        assertEquals("1 completion over 1 day", card.evidence)
    }

    @Test
    fun `long histories report their span in months`() {
        val quest = recurringQuest(id = "gym", attribute = Attribute.Body)
        val history = completions(quest, date("2026-01-05"), date("2026-07-01"))

        val card = card(buildProfile(listOf(quest), history, today), Attribute.Body)

        // 2026-01-05..2026-07-17 inclusive is 194 days → ⌈194/30.44⌉ = 7 months.
        assertEquals("2 completions over 7 months", card.evidence)
    }

    @Test
    fun `duplicate records in one period credit once, everywhere on the profile`() {
        val quest = recurringQuest(id = "gym", attribute = Attribute.Body)
        val duplicated = listOf(
            completion(quest, date("2026-07-10")),
            completion(quest, date("2026-07-10")),
        )

        val summary = buildProfile(listOf(quest), duplicated, today)

        assertEquals(1, card(summary, Attribute.Body).completions)
        assertEquals(1, summary.lifetimeCompletions)
    }

    @Test
    fun `lifetime total counts recurring and side quests alike`() {
        val recurring = recurringQuest(id = "gym", attribute = Attribute.Body)
        val side = sideQuest(id = "dentist")
        val history = completions(recurring, date("2026-07-15"), date("2026-07-16")) +
            completion(side, date("2026-07-16"))

        val summary = buildProfile(listOf(recurring, side), history, today)

        assertEquals(3, summary.lifetimeCompletions)
        // …but the side quest never appears in any attribute's evidence.
        assertEquals(2, card(summary, Attribute.Body).completions)
    }

    @Test
    fun `retired quests form the completed-chapters archive, newest first`() {
        val older = recurringQuest(
            id = "couch-to-5k", title = "Couch to 5k", attribute = Attribute.Body,
            status = QuestStatus.Retired, createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val newer = recurringQuest(
            id = "meditation", title = "Morning meditation", attribute = Attribute.Mind,
            status = QuestStatus.Retired, createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        )
        val active = recurringQuest(id = "gym", attribute = Attribute.Body)
        val history = completions(older, date("2026-01-02"), date("2026-01-03"), date("2026-01-04"))

        val summary = buildProfile(listOf(older, newer, active), history, today)

        assertEquals(listOf("Morning meditation", "Couch to 5k"), summary.chapters.map { it.quest.title })
        assertEquals(listOf(0, 3), summary.chapters.map { it.completions })
    }

    @Test
    fun `progress fraction is measured between rank thresholds`() {
        val quest = recurringQuest(id = "gym", attribute = Attribute.Body)
        // 10 completions → 10 points: rank 1 (5) → rank 2 (15), halfway.
        val history = (1..10).map { completion(quest, date("2026-07-01").plusDays(it.toLong())) }

        val card = card(buildProfile(listOf(quest), history, today), Attribute.Body)

        assertEquals(1, card.rank)
        assertEquals(0.5, card.progressToNextRank, 1e-9)
    }
}
