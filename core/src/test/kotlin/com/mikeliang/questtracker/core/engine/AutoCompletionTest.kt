package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completion
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.health.HealthMetric
import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.AutoTracking
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.ProgressionTarget
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.QuestType
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoCompletionTest {

    private val today = date("2026-07-16") // Thursday
    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val steps8k = AutoTracking(HealthMetric.Steps, dailyTarget = 8_000.0)

    @Test
    fun `records an auto-tracked completion when the day hits target`() {
        val quest = recurringQuest(autoTracking = steps8k)

        val record = autoCompletionFor(quest, today, HealthReading.Available(8_450.0), emptyList(), now)

        assertNotNull(record)
        assertEquals(quest.id, record!!.questId)
        assertEquals(today, record.periodStart)
        assertEquals(now, record.completedAt)
        assertEquals(CompletionSource.AutoTracked, record.source)
    }

    @Test
    fun `exactly on target counts`() {
        val quest = recurringQuest(autoTracking = steps8k)

        assertNotNull(autoCompletionFor(quest, today, HealthReading.Available(8_000.0), emptyList(), now))
    }

    @Test
    fun `below target records nothing`() {
        val quest = recurringQuest(autoTracking = steps8k)

        assertNull(autoCompletionFor(quest, today, HealthReading.Available(7_999.9), emptyList(), now))
    }

    @Test
    fun `unavailable data is neutral, never a zero`() {
        val quest = recurringQuest(autoTracking = steps8k)

        assertNull(autoCompletionFor(quest, today, HealthReading.Unavailable, emptyList(), now))
    }

    @Test
    fun `an already banked period is never double-credited`() {
        val quest = recurringQuest(autoTracking = steps8k)
        val banked = listOf(completion(quest, today))

        assertNull(autoCompletionFor(quest, today, HealthReading.Available(9_000.0), banked, now))
    }

    @Test
    fun `a manual completion suppresses auto credit for the same period`() {
        val quest = recurringQuest(cadence = Cadence.Weekly, autoTracking = steps8k)
        // Manually completed Monday; Thursday's reading is the same ISO week.
        val banked = listOf(completion(quest, date("2026-07-13"), source = CompletionSource.Manual))

        assertNull(autoCompletionFor(quest, today, HealthReading.Available(9_000.0), banked, now))
    }

    @Test
    fun `another quest's completion does not suppress this one`() {
        val quest = recurringQuest(id = "mine", autoTracking = steps8k)
        val other = recurringQuest(id = "other", autoTracking = steps8k)
        val banked = listOf(completion(other, today))

        assertNotNull(autoCompletionFor(quest, today, HealthReading.Available(9_000.0), banked, now))
    }

    @Test
    fun `weekly quest credits the ISO week of the reading's date`() {
        val quest = recurringQuest(cadence = Cadence.Weekly, autoTracking = steps8k)

        val record = autoCompletionFor(quest, today, HealthReading.Available(9_000.0), emptyList(), now)

        assertEquals(date("2026-07-13"), record!!.periodStart) // Monday of that week
    }

    @Test
    fun `a late reading credits the period containing its own date, not today's`() {
        val quest = recurringQuest(autoTracking = steps8k)
        val twoDaysAgo = today.minusDays(2)

        val record = autoCompletionFor(quest, twoDaysAgo, HealthReading.Available(9_000.0), emptyList(), now)

        assertEquals(twoDaysAgo, record!!.periodStart)
    }

    @Test
    fun `progression quests freeze the escalation level at record time`() {
        val quest = recurringQuest(
            type = QuestType.Progression,
            progression = ProgressionTarget(8_000.0, "steps", escalationLevel = 2),
            autoTracking = steps8k,
        )

        val record = autoCompletionFor(quest, today, HealthReading.Available(9_000.0), emptyList(), now)

        assertEquals(2, record!!.escalationLevel)
    }

    @Test
    fun `retired quests never auto-complete`() {
        val quest = recurringQuest(status = QuestStatus.Retired, autoTracking = steps8k)

        assertNull(autoCompletionFor(quest, today, HealthReading.Available(9_000.0), emptyList(), now))
    }

    @Test
    fun `quests without auto-tracking never auto-complete`() {
        val quest = recurringQuest()

        assertNull(autoCompletionFor(quest, today, HealthReading.Available(9_000.0), emptyList(), now))
    }

    @Test
    fun `side quests never auto-complete`() {
        assertNull(autoCompletionFor(sideQuest(), today, HealthReading.Available(9_000.0), emptyList(), now))
    }
}
