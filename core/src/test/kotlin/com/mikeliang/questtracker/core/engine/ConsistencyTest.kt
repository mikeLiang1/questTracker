package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.completions
import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.recurringQuest
import com.mikeliang.questtracker.core.sideQuest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConsistencyTest {

    private val zone = ZoneOffset.UTC
    private val today = date("2026-07-16")

    /** The 28 evaluated dates for a daily quest as of [today]: 2026-06-18..2026-07-15. */
    private fun window(except: Set<LocalDate> = emptySet()): List<LocalDate> =
        (1L..28L).map { today.minusDays(it) }.filterNot { it in except }

    @Test
    fun `perfect history scores 1_0 with nothing absorbed`() {
        val quest = recurringQuest()
        val history = completions(quest, *window().toTypedArray())

        val score = consistencyScore(quest, history, today, zone)

        assertEquals(ConsistencyScore(28, 28, 0, 1.0), score)
    }

    @Test
    fun `misses within the rest allowance are neutral`() {
        val quest = recurringQuest()
        val missed = setOf(date("2026-06-25"), date("2026-07-01"), date("2026-07-10"))
        val history = completions(quest, *window(except = missed).toTypedArray())

        val score = consistencyScore(quest, history, today, zone)

        // 3 misses, allowance ⌈28/7⌉ = 4 → all absorbed.
        assertEquals(ConsistencyScore(28, 25, 3, 1.0), score)
    }

    @Test
    fun `misses beyond the allowance lower the rate gently`() {
        val quest = recurringQuest()
        val missed = (1L..6L).map { date("2026-06-20").plusDays(it * 3) }.toSet()
        val history = completions(quest, *window(except = missed).toTypedArray())

        val score = consistencyScore(quest, history, today, zone)

        // 6 misses, 4 absorbed → 22 / (28 − 4) = 22/24.
        assertEquals(ConsistencyScore(28, 22, 4, 22.0 / 24.0), score)
    }

    @Test
    fun `today is never counted as a miss before it ends`() {
        val quest = recurringQuest()
        val history = completions(quest, *window().toTypedArray()) // nothing today

        assertEquals(1.0, consistencyScore(quest, history, today, zone).rate)
    }

    @Test
    fun `periods before the quest existed are not evaluated`() {
        val createdAt = Instant.parse("2026-07-13T09:00:00Z")
        val quest = recurringQuest(createdAt = createdAt)
        val history = completions(quest, date("2026-07-13"), date("2026-07-15")) // missed the 14th

        val score = consistencyScore(quest, history, today, zone)

        // 3 evaluated days, 1 miss, allowance ⌈3/7⌉ = 1 → absorbed.
        assertEquals(ConsistencyScore(3, 2, 1, 1.0), score)
    }

    @Test
    fun `a quest created today has no evidence and scores a neutral 1_0`() {
        val quest = recurringQuest(createdAt = Instant.parse("2026-07-16T08:00:00Z"))

        val score = consistencyScore(quest, emptyList(), today, zone)

        assertEquals(ConsistencyScore(0, 0, 0, 1.0), score)
    }

    @Test
    fun `weekly quests use the 12-week window and 1-in-6 absorption`() {
        val quest = recurringQuest(cadence = Cadence.Weekly)
        // Evaluated week starts: 2026-04-20 .. 2026-07-06. Miss 3 of them.
        val weekStarts = (0L..11L).map { date("2026-04-20").plusWeeks(it) }
        val completedWeeks = weekStarts.drop(3)
        val history = completions(quest, *completedWeeks.toTypedArray())

        val score = consistencyScore(quest, history, today, zone)

        // 3 misses, allowance ⌈12/6⌉ = 2 → 9 / (12 − 2) = 0.9.
        assertEquals(ConsistencyScore(12, 9, 2, 0.9), score)
    }

    @Test
    fun `monthly quests use the 6-month window`() {
        val quest = recurringQuest(cadence = Cadence.Monthly)
        // Evaluated months: Jan–Jun 2026. Miss one.
        val monthStarts = (1..6).map { LocalDate.of(2026, it, 1) }
        val history = completions(quest, *monthStarts.drop(1).toTypedArray())

        val score = consistencyScore(quest, history, today, zone)

        // 1 miss, allowance ⌈6/6⌉ = 1 → absorbed.
        assertEquals(ConsistencyScore(6, 5, 1, 1.0), score)
    }

    @Test
    fun `an abandoned stretch reads as a low rate - not a broken anything`() {
        val quest = recurringQuest()
        // Only 7 completions in the whole window.
        val history = completions(quest, *(1L..7L).map { today.minusDays(it) }.toTypedArray())

        val score = consistencyScore(quest, history, today, zone)

        // 21 misses, 4 absorbed → 7/24. Never negative, never zeroing banked gains.
        assertEquals(ConsistencyScore(28, 7, 4, 7.0 / 24.0), score)
    }

    @Test
    fun `side quests have no consistency`() {
        assertThrows<IllegalArgumentException> {
            consistencyScore(sideQuest(), emptyList(), today, zone)
        }
    }

    @Test
    fun `quest creation date respects the zone`() {
        // Created 2026-07-12T20:00 in Sydney (+10) = 2026-07-13 local: the 12th is pre-creation.
        val quest = recurringQuest(createdAt = Instant.parse("2026-07-12T20:00:00Z"))

        val score = consistencyScore(quest, emptyList(), today, java.time.ZoneId.of("Australia/Sydney"))

        assertEquals(3, score.evaluatedPeriods) // 13th, 14th, 15th
    }
}
