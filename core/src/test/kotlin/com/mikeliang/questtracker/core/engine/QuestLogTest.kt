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
    fun `days are newest-first and items within a day newest-first`() {
        val quest = recurringQuest(id = "gym", title = "Gym hour")
        val log = buildQuestLog(
            quests = listOf(quest),
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
        // On the 15th: the 20:00 entry outranks the noon completion.
        val fifteenth = log.last().items
        assertTrue(fifteenth.first() is QuestLogItem.Entry)
        assertTrue(fifteenth.last() is QuestLogItem.Completion)
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
