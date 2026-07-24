package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.journalEntry
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.recurringQuest
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuestLogTest {

    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun `empty inputs produce an empty log`() {
        assertTrue(buildQuestLog(emptyList(), emptyList(), emptyList(), utc).isEmpty())
    }

    @Test
    fun `days are newest-first and unlinked items within a day are newest-first`() {
        val quest = recurringQuest(id = "gym", title = "Gym hour")
        val log = buildQuestLog(
            quests = listOf(quest),
            // A manual tick at noon, and a free-form entry at 20:00 that banked nothing.
            completions = listOf(completion(quest, date("2026-07-15"))),
            entries = listOf(
                journalEntry(id = "e1", entryDate = date("2026-07-16")),
                journalEntry(
                    id = "e2",
                    entryDate = date("2026-07-15"),
                    createdAt = Instant.parse("2026-07-15T20:00:00Z"),
                ),
            ),
            zone = utc,
        )

        assertEquals(listOf(date("2026-07-16"), date("2026-07-15")), log.map { it.date })
        // Nothing is paired here, so the 20:00 entry outranks the noon completion.
        val fifteenth = log.last().items
        assertTrue(fifteenth.first() is QuestLogItem.Entry)
        assertTrue(fifteenth.last() is QuestLogItem.Completion)
    }

    @Test
    fun `a journal-linked completion sits directly above the entry that banked it`() {
        val linked = recurringQuest(id = "reading", title = "Read 20 min")
        val other = recurringQuest(id = "gym", title = "Gym hour")
        val day = date("2026-07-16")
        // The entry (21:00) banked the reading quest; the gym tick (noon) is unrelated.
        val entry = journalEntry(id = "wrote", entryDate = day, questIds = setOf(linked.id))
        val log = buildQuestLog(
            quests = listOf(linked, other),
            completions = listOf(completion(linked, day), completion(other, day)),
            entries = listOf(entry),
            zone = utc,
        )

        val items = log.single().items
        // Reading completion immediately above its entry; the unrelated gym tick keeps
        // its own newest-first slot rather than being hoisted with it.
        assertEquals(
            listOf("completion:reading", "entry:wrote", "completion:gym"),
            items.map { item ->
                when (item) {
                    is QuestLogItem.Entry -> "entry:${item.entry.id.value}"
                    is QuestLogItem.Completion -> "completion:${item.record.questId.value}"
                }
            },
        )
    }

    @Test
    fun `a completion lands on the day it happened, not its period start`() {
        val weekly = recurringQuest(id = "weekly", cadence = Cadence.Weekly)
        // Completed Wednesday the 15th; the ISO week's periodStart is Monday the 13th.
        val record = completion(weekly, date("2026-07-15"))
        assertEquals(date("2026-07-13"), record.periodStart)

        val log = buildQuestLog(listOf(weekly), listOf(record), emptyList(), utc)

        assertEquals(listOf(date("2026-07-15")), log.map { it.date })
    }

    @Test
    fun `the completion day follows the zone`() {
        val quest = recurringQuest(id = "gym")
        // 15:30 UTC on the 16th is already the 17th in Sydney.
        val record = completion(
            quest,
            date("2026-07-16"),
            completedAt = Instant.parse("2026-07-16T15:30:00Z"),
        )

        val log = buildQuestLog(listOf(quest), listOf(record), emptyList(), ZoneId.of("Australia/Sydney"))

        assertEquals(listOf(date("2026-07-17")), log.map { it.date })
    }

    @Test
    fun `completions resolve their quest's title and frozen attribute`() {
        val quest = recurringQuest(id = "gym", title = "Gym hour", attribute = Attribute.Body)
        val log = buildQuestLog(listOf(quest), listOf(completion(quest, date("2026-07-16"))), emptyList(), utc)

        val item = log.single().items.single() as QuestLogItem.Completion
        assertEquals("Gym hour", item.questTitle)
        assertEquals(Attribute.Body, item.attribute)
    }

    @Test
    fun `a completion whose quest is gone renders gracefully, not fatally`() {
        val quest = recurringQuest(id = "gone")
        val log = buildQuestLog(emptyList(), listOf(completion(quest, date("2026-07-16"))), emptyList(), utc)

        val item = log.single().items.single() as QuestLogItem.Completion
        assertNull(item.questTitle)
    }

    @Test
    fun `a pre-freeze record falls back to the quest's current attribute`() {
        val quest = recurringQuest(id = "gym", attribute = Attribute.Discipline)
        val preFreeze = completion(quest, date("2026-07-16")).copy(attribute = null, basePoints = null)

        val log = buildQuestLog(listOf(quest), listOf(preFreeze), emptyList(), utc)

        val item = log.single().items.single() as QuestLogItem.Completion
        assertEquals(Attribute.Discipline, item.attribute)
    }

    @Test
    fun `a quest-scoped entry appears on its day tagged with the quest it counted toward`() {
        val quest = recurringQuest(id = "journal", title = "One line of journal")
        val scoped = journalEntry(
            id = "scoped",
            entryDate = date("2026-07-16"),
            questIds = setOf(quest.id),
        )
        val freeform = journalEntry(id = "free", entryDate = date("2026-07-16"))

        val log = buildQuestLog(
            quests = listOf(quest),
            completions = listOf(completion(quest, date("2026-07-16"))),
            entries = listOf(scoped, freeform),
            zone = utc,
        )

        val entries = log.single().items.filterIsInstance<QuestLogItem.Entry>()
        assertEquals(setOf("scoped", "free"), entries.map { it.entry.id.value }.toSet())
        // The scoped entry names its quest; the free-form one names nothing.
        assertEquals(
            listOf("One line of journal"),
            entries.single { it.entry.id.value == "scoped" }.linkedQuestTitles,
        )
        assertEquals(emptyList<String>(), entries.single { it.entry.id.value == "free" }.linkedQuestTitles)
        assertEquals(1, log.single().items.filterIsInstance<QuestLogItem.Completion>().size)
    }

    @Test
    fun `a scoped entry whose quest was deleted still shows, untagged`() {
        val scoped = journalEntry(
            id = "orphan",
            entryDate = date("2026-07-16"),
            questIds = setOf(recurringQuest(id = "gone").id),
        )

        val log = buildQuestLog(emptyList(), emptyList(), listOf(scoped), utc)

        val entry = log.single().items.filterIsInstance<QuestLogItem.Entry>().single()
        assertEquals("orphan", entry.entry.id.value)
        assertEquals(emptyList<String>(), entry.linkedQuestTitles)
    }

    @Test
    fun `journalEntriesFor returns a quest's entries newest first and nobody else's`() {
        val questId = recurringQuest(id = "journal").id
        val older = journalEntry(
            id = "older",
            entryDate = date("2026-07-15"),
            questIds = setOf(questId),
        )
        val newer = journalEntry(
            id = "newer",
            entryDate = date("2026-07-16"),
            questIds = setOf(questId, recurringQuest(id = "also").id),
        )
        val elsewhere = journalEntry(
            id = "elsewhere",
            entryDate = date("2026-07-16"),
            questIds = setOf(recurringQuest(id = "other").id),
        )
        val freeform = journalEntry(id = "free", entryDate = date("2026-07-16"))

        val entries = journalEntriesFor(questId, listOf(older, newer, elsewhere, freeform))

        assertEquals(listOf("newer", "older"), entries.map { it.id.value })
    }

    @Test
    fun `journalEntriesFor scoped to a day returns only that day's writing`() {
        val questId = recurringQuest(id = "journal").id
        val yesterday = journalEntry(
            id = "yesterday",
            entryDate = date("2026-07-15"),
            questIds = setOf(questId),
        )
        val today = journalEntry(
            id = "today",
            entryDate = date("2026-07-16"),
            questIds = setOf(questId),
        )

        val entries = journalEntriesFor(questId, listOf(yesterday, today), on = date("2026-07-16"))

        assertEquals(listOf("today"), entries.map { it.id.value })
    }

    @Test
    fun `a day with no writing for the quest shows no entries`() {
        // The reported case: cleared before journalling existed, written on a later
        // day. Opening the older completion must not surface the newer entry.
        val questId = recurringQuest(id = "journal").id
        val written = journalEntry(
            id = "later",
            entryDate = date("2026-07-16"),
            questIds = setOf(questId),
        )

        val entries = journalEntriesFor(questId, listOf(written), on = date("2026-07-15"))

        assertEquals(emptyList<String>(), entries.map { it.id.value })
    }

    @Test
    fun `entries group by their frozen entryDate regardless of createdAt's zone-shifted day`() {
        // Written at 23:30 local but createdAt is next-day UTC: entryDate wins.
        val entry = journalEntry(
            id = "e1",
            entryDate = date("2026-07-16"),
            createdAt = Instant.parse("2026-07-17T04:30:00Z"),
        )

        val log = buildQuestLog(emptyList(), emptyList(), listOf(entry), utc)

        assertEquals(listOf(date("2026-07-16")), log.map { it.date })
    }
}
