package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.date
import com.mikeliang.questtracker.core.model.Cadence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuestPeriodsTest {

    @Test
    fun `daily period is the single date`() {
        val period = periodContaining(date("2026-07-16"), Cadence.Daily)

        assertEquals(date("2026-07-16"), period.start)
        assertEquals(date("2026-07-16"), period.endInclusive)
    }

    @Test
    fun `weekly period is ISO Monday to Sunday`() {
        // 2026-07-16 is a Thursday.
        val period = periodContaining(date("2026-07-16"), Cadence.Weekly)

        assertEquals(date("2026-07-13"), period.start)
        assertEquals(date("2026-07-19"), period.endInclusive)
    }

    @Test
    fun `weekly period spans a year boundary`() {
        val period = periodContaining(date("2026-01-01"), Cadence.Weekly)

        assertEquals(date("2025-12-29"), period.start)
        assertEquals(date("2026-01-04"), period.endInclusive)
    }

    @Test
    fun `a Monday starts its own week`() {
        val period = periodContaining(date("2026-07-13"), Cadence.Weekly)

        assertEquals(date("2026-07-13"), period.start)
    }

    @Test
    fun `monthly period covers the calendar month`() {
        val period = periodContaining(date("2026-07-16"), Cadence.Monthly)

        assertEquals(date("2026-07-01"), period.start)
        assertEquals(date("2026-07-31"), period.endInclusive)
    }

    @Test
    fun `monthly period handles leap February`() {
        assertEquals(date("2028-02-29"), periodContaining(date("2028-02-10"), Cadence.Monthly).endInclusive)
        assertEquals(date("2026-02-28"), periodContaining(date("2026-02-10"), Cadence.Monthly).endInclusive)
    }

    @Test
    fun `previous steps back exactly one period`() {
        assertEquals(
            date("2026-02-28"),
            periodContaining(date("2026-03-01"), Cadence.Daily).previous().start,
        )
        assertEquals(
            date("2026-07-06"),
            periodContaining(date("2026-07-16"), Cadence.Weekly).previous().start,
        )
        assertEquals(
            date("2026-06-01"),
            periodContaining(date("2026-07-16"), Cadence.Monthly).previous().start,
        )
    }

    @Test
    fun `next steps forward exactly one period`() {
        assertEquals(
            date("2026-07-20"),
            periodContaining(date("2026-07-16"), Cadence.Weekly).next().start,
        )
        assertEquals(
            date("2026-08-01"),
            periodContaining(date("2026-07-16"), Cadence.Monthly).next().start,
        )
    }

    @Test
    fun `contains covers both boundaries`() {
        val week = periodContaining(date("2026-07-16"), Cadence.Weekly)

        assertTrue(date("2026-07-13") in week)
        assertTrue(date("2026-07-19") in week)
        assertFalse(date("2026-07-12") in week)
        assertFalse(date("2026-07-20") in week)
    }
}
