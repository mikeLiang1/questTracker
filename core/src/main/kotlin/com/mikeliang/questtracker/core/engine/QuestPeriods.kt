package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Cadence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One period of a cadence's clock: a calendar day, an ISO week (Monday–Sunday), or a
 * calendar month. All completion crediting, dedupe, consistency, and reminder
 * suppression is period-based.
 */
data class QuestPeriod(
    val cadence: Cadence,
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    fun previous(): QuestPeriod = periodContaining(start.minusDays(1), cadence)

    fun next(): QuestPeriod = periodContaining(endInclusive.plusDays(1), cadence)
}

/** The period of [cadence] that contains [date]. */
fun periodContaining(date: LocalDate, cadence: Cadence): QuestPeriod = when (cadence) {
    Cadence.Daily -> QuestPeriod(cadence, date, date)
    Cadence.Weekly -> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        QuestPeriod(cadence, start, start.plusDays(6))
    }
    Cadence.Monthly -> QuestPeriod(
        cadence,
        date.withDayOfMonth(1),
        date.with(TemporalAdjusters.lastDayOfMonth()),
    )
}

/** Start date of the [cadence] period containing [date] — the completion dedupe key. */
fun periodStartFor(date: LocalDate, cadence: Cadence): LocalDate =
    periodContaining(date, cadence).start
