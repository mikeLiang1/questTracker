package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.progressionQuest
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttributeAccrualTest {

    private val start = date("2026-06-01")

    @Test
    fun `daily completions earn one point each`() {
        val quest = recurringQuest(attribute = Attribute.Body)
        val history = completions(quest, *(0L..9L).map { start.plusDays(it) }.toTypedArray())

        val progress = attributeProgress(listOf(quest), history).getValue(Attribute.Body)

        assertEquals(10.0, progress.points)
        assertEquals(10, progress.completions)
    }

    @Test
    fun `weekly and monthly completions carry their heavier base`() {
        val weekly = recurringQuest(id = "w", cadence = Cadence.Weekly, attribute = Attribute.Mind)
        val monthly = recurringQuest(id = "m", cadence = Cadence.Monthly, attribute = Attribute.Discipline)
        val history =
            completions(weekly, date("2026-06-01"), date("2026-06-08")) +
                completions(monthly, date("2026-06-15"))

        val progress = attributeProgress(listOf(weekly, monthly), history)

        assertEquals(6.0, progress.getValue(Attribute.Mind).points)
        assertEquals(10.0, progress.getValue(Attribute.Discipline).points)
    }

    @Test
    fun `side quests never feed attributes`() {
        val side = sideQuest()
        val history = completions(side, start)

        val progress = attributeProgress(listOf(side), history)

        Attribute.entries.forEach { assertEquals(0.0, progress.getValue(it).points) }
    }

    @Test
    fun `duplicate completions in one period credit once`() {
        val quest = recurringQuest()
        val history = listOf(
            completion(quest, start, completedAt = Instant.parse("2026-06-01T08:00:00Z")),
            completion(quest, start, completedAt = Instant.parse("2026-06-01T20:00:00Z")),
        )

        val progress = attributeProgress(listOf(quest), history).getValue(Attribute.Body)

        assertEquals(1.0, progress.points)
        assertEquals(1, progress.completions)
    }

    @Test
    fun `progression quests diminish to half rate after 15 completions at a level`() {
        val quest = progressionQuest()
        val history = completions(quest, *(0L..19L).map { start.plusDays(it) }.toTypedArray())

        val progress = attributeProgress(listOf(quest), history).getValue(Attribute.Body)

        // 15 full + 5 halved = 17.5
        assertEquals(17.5, progress.points)
    }

    @Test
    fun `escalating restores the full rate`() {
        val quest = progressionQuest()
        val atLevelZero = (0L..15L).map { // 16 completions: 15 full + 1 halved
            completion(quest, start.plusDays(it), escalationLevel = 0)
        }
        val atLevelOne = (16L..18L).map { // 3 completions, full rate again
            completion(quest, start.plusDays(it), escalationLevel = 1)
        }

        val progress = attributeProgress(listOf(quest), atLevelZero + atLevelOne)
            .getValue(Attribute.Body)

        assertEquals(15.0 + 0.5 + 3.0, progress.points)
    }

    @Test
    fun `maintenance quests never diminish`() {
        val quest = recurringQuest()
        val history = completions(quest, *(0L..29L).map { start.plusDays(it) }.toTypedArray())

        assertEquals(30.0, attributeProgress(listOf(quest), history).getValue(Attribute.Body).points)
    }

    @Test
    fun `quests sharing an attribute aggregate`() {
        val gym = recurringQuest(id = "gym", attribute = Attribute.Body)
        val run = recurringQuest(id = "run", title = "Morning run", attribute = Attribute.Body)
        val history = completions(gym, start, start.plusDays(1)) + completions(run, start)

        assertEquals(3.0, attributeProgress(listOf(gym, run), history).getValue(Attribute.Body).points)
    }

    @Test
    fun `every attribute is present with milestone context even when untouched`() {
        val quest = recurringQuest(attribute = Attribute.Body)
        val history = completions(quest, *(0L..16L).map { start.plusDays(it) }.toTypedArray())

        val progress = attributeProgress(listOf(quest), history)

        val body = progress.getValue(Attribute.Body) // 17 points → rank 2, next at 30
        assertEquals(2, body.rank)
        assertEquals("Committed", body.title)
        assertEquals("Consistent", body.nextTitle)
        assertEquals(13.0, body.pointsToNextRank)

        val social = progress.getValue(Attribute.Social)
        assertEquals(0, social.rank)
        assertEquals("Unwritten", social.title)
        assertEquals(5.0, social.pointsToNextRank)
    }

    @Test
    fun `editing the attribute leaves banked points on the original attribute`() {
        val before = recurringQuest(attribute = Attribute.Body)
        val banked = completions(before, start, start.plusDays(1)) // frozen as Body
        val after = editQuest(
            before,
            QuestEdit.EditRecurring(before.title, Cadence.Daily, Attribute.Mind, null),
            banked,
            start.plusDays(2),
        )
        val newCredit = completions(after, start.plusDays(2)) // frozen as Mind

        val progress = attributeProgress(listOf(after), banked + newCredit)

        assertEquals(2.0, progress.getValue(Attribute.Body).points)
        assertEquals(1.0, progress.getValue(Attribute.Mind).points)
    }

    @Test
    fun `editing the cadence never re-prices banked completions`() {
        val before = recurringQuest(cadence = Cadence.Weekly, attribute = Attribute.Mind)
        val banked = completions(before, date("2026-06-01"), date("2026-06-08")) // frozen at 3.0 each
        val after = editQuest(
            before,
            QuestEdit.EditRecurring(before.title, Cadence.Daily, Attribute.Mind, null),
            banked,
            date("2026-06-15"),
        )

        assertEquals(6.0, attributeProgress(listOf(after), banked).getValue(Attribute.Mind).points)
    }

    @Test
    fun `editing the cadence never collapses banked credits`() {
        val before = recurringQuest(cadence = Cadence.Daily)
        // Three daily credits inside one calendar week.
        val banked = completions(before, date("2026-06-01"), date("2026-06-02"), date("2026-06-03"))
        val after = editQuest(
            before,
            QuestEdit.EditRecurring(before.title, Cadence.Weekly, Attribute.Body, null),
            banked,
            date("2026-06-15"),
        )

        val progress = attributeProgress(listOf(after), banked).getValue(Attribute.Body)

        assertEquals(3.0, progress.points)
        assertEquals(3, progress.completions)
    }

    @Test
    fun `records banked before freezing fall back to the quest's current context`() {
        val quest = recurringQuest(cadence = Cadence.Weekly, attribute = Attribute.Discipline)
        val unfrozen = completion(quest, start).copy(attribute = null, basePoints = null)

        val progress = attributeProgress(listOf(quest), listOf(unfrozen))

        assertEquals(3.0, progress.getValue(Attribute.Discipline).points)
    }

    @Test
    fun `maintenance records stay full base even after a target is added later`() {
        val before = recurringQuest(type = QuestType.Maintenance)
        // 20 maintenance credits — no escalation level frozen on them.
        val banked = completions(before, *(0L..19L).map { start.plusDays(it) }.toTypedArray())
        val after = editQuest(
            before,
            QuestEdit.EditRecurring(before.title, Cadence.Daily, Attribute.Body, null, TargetEdit.Add(8000.0, "steps")),
            banked,
            start.plusDays(20),
        )

        // Never diminished: 20 full points, not 15 + 5 halved.
        assertEquals(20.0, attributeProgress(listOf(after), banked).getValue(Attribute.Body).points)
    }
}
