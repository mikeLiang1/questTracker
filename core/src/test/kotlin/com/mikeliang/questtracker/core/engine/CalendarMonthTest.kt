package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.date
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarMonthTest {

    private fun flatCells(view: CalendarMonthView): List<CalendarCell> = view.weeks.flatten()

    @Test
    fun `weeks are Monday-first and every row has seven days`() {
        val view = buildCalendarMonth(YearMonth.of(2026, 7), emptySet(), date("2026-07-24"))

        view.weeks.forEach { week -> assertEquals(7, week.size) }
        view.weeks.forEach { week ->
            assertEquals(DayOfWeek.MONDAY, week.first().date.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, week.last().date.dayOfWeek)
        }
    }

    @Test
    fun `a mid-week first of the month gets the right leading blanks`() {
        // 1 July 2026 is a Wednesday: Mon+Tue of the prior month pad the first row.
        val view = buildCalendarMonth(YearMonth.of(2026, 7), emptySet(), date("2026-07-24"))
        val firstRow = view.weeks.first()

        assertEquals(date("2026-06-29"), firstRow[0].date) // Monday
        assertFalse(firstRow[0].inMonth)
        assertFalse(firstRow[1].inMonth) // Tuesday, still June
        assertEquals(date("2026-07-01"), firstRow[2].date)
        assertTrue(firstRow[2].inMonth)
    }

    @Test
    fun `a month whose first is a Monday has no leading blanks`() {
        // 1 June 2026 is a Monday.
        val view = buildCalendarMonth(YearMonth.of(2026, 6), emptySet(), date("2026-06-15"))
        val first = view.weeks.first().first()

        assertEquals(date("2026-06-01"), first.date)
        assertTrue(first.inMonth)
    }

    @Test
    fun `presence is marked exactly for dates in the present set`() {
        val present = setOf(date("2026-07-03"), date("2026-07-20"))
        val view = buildCalendarMonth(YearMonth.of(2026, 7), present, date("2026-07-24"))

        val marked = flatCells(view).filter { it.hasContent }.map { it.date }.toSet()
        assertEquals(present, marked)
    }

    @Test
    fun `a month with no data has no marked days`() {
        val view = buildCalendarMonth(YearMonth.of(2026, 7), emptySet(), date("2026-07-24"))
        assertTrue(flatCells(view).none { it.hasContent })
    }

    @Test
    fun `today is flagged on exactly one cell and future days are flagged`() {
        val today = date("2026-07-24")
        val view = buildCalendarMonth(YearMonth.of(2026, 7), emptySet(), today)

        val todays = flatCells(view).filter { it.isToday }
        assertEquals(1, todays.size)
        assertEquals(today, todays.single().date)

        assertTrue(flatCells(view).filter { it.isFuture }.all { it.date.isAfter(today) })
        assertTrue(flatCells(view).any { it.date == date("2026-07-25") && it.isFuture })
        assertFalse(view.weeks.flatten().first { it.date == today }.isFuture)
    }

    @Test
    fun `a month entirely in the past has no today flag`() {
        val view = buildCalendarMonth(YearMonth.of(2026, 6), emptySet(), date("2026-07-24"))
        assertTrue(flatCells(view).none { it.isToday })
        assertTrue(flatCells(view).filter { it.inMonth }.none { it.isFuture })
    }

    @Test
    fun `a 31-day month starting late spills into six weeks`() {
        // August 2026: 1 Aug is a Saturday, so the grid needs six rows to fit the 31st.
        val view = buildCalendarMonth(YearMonth.of(2026, 8), emptySet(), date("2026-08-15"))
        assertEquals(6, view.weeks.size)
        assertTrue(flatCells(view).any { it.date == date("2026-08-31") && it.inMonth })
    }
}
