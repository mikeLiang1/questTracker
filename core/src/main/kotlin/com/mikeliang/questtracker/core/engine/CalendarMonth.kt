package com.mikeliang.questtracker.core.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * One cell of the calendar day-finder grid. The grid is a *view* over the Quest Log —
 * [hasContent] is presence-only (something happened that day), never a count or an
 * intensity. A day with nothing on it is simply blank: empty is not failure (design
 * foundation §5), so there is deliberately no "missed" flag here for the UI to shade red.
 *
 * @property inMonth false for the leading/trailing days of adjacent months that pad the
 * grid to whole Monday–Sunday weeks; those render faint and are not selectable.
 * @property isFuture after [today]; inert and unmarked (nothing lives there yet), framed
 * no differently from any other empty day.
 */
data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val hasContent: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/** A month laid out as whole Monday-first weeks for the calendar grid. */
data class CalendarMonthView(
    val month: YearMonth,
    val weeks: List<List<CalendarCell>>,
)

/**
 * Lays [month] out as a grid of whole ISO weeks (Monday-first, matching the engine's
 * Monday-start weeks — see [periodContaining]). Leading days spill back to the Monday on
 * or before the 1st and trailing days run to the Sunday on or after the last, so every
 * row has seven cells and the month's own days always land under the right weekday.
 *
 * [presentDays] is the set of local dates that have anything on the Quest Log timeline —
 * pass the dates already grouped by `buildQuestLog`, so the grid and the timeline can
 * never disagree about which day a thing belongs to. A cell is marked purely by
 * membership: presence, never magnitude.
 *
 * [today] is supplied (never read from a clock here) so this stays pure and testable; it
 * only drives the neutral today/future flags.
 */
fun buildCalendarMonth(
    month: YearMonth,
    presentDays: Set<LocalDate>,
    today: LocalDate,
): CalendarMonthView {
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    val cells = generateSequence(gridStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(gridEnd) }
        .map { date ->
            CalendarCell(
                date = date,
                inMonth = YearMonth.from(date) == month,
                hasContent = date in presentDays,
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
        .toList()

    return CalendarMonthView(month = month, weeks = cells.chunked(7))
}
