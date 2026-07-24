package com.mikeliang.questtracker.ui.questlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikeliang.questtracker.core.engine.CalendarCell
import com.mikeliang.questtracker.core.engine.buildCalendarMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The calendar day-finder: a month grid over the Quest Log. A day with anything on it
 * carries a single presence dot (never a count or a heat shade); a day with nothing is
 * blank — empty is not failure. Tapping a dotted day jumps the Log to it via
 * [onSelectDay]; empty, future, and adjacent-month padding cells are inert. It is a
 * finder, not a loop: it opens on request and dismisses freely, and nothing here is
 * required.
 *
 * @param presentDays the local dates that have anything on the timeline — pass the days
 * `buildQuestLog` already grouped, so the grid never disagrees with what the Log shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    presentDays: Set<LocalDate>,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentMonth = YearMonth.from(today)
    // Don't wander into empty months: back to the earliest month with data, forward to
    // the present (nothing lives in the future yet).
    val earliestMonth = presentDays.minOrNull()?.let(YearMonth::from) ?: currentMonth

    var month by rememberSaveable(stateSaver = YearMonthSaver) {
        mutableStateOf(currentMonth)
    }
    val view = buildCalendarMonth(month, presentDays, today)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            MonthHeader(
                month = month,
                canGoBack = month > earliestMonth,
                canGoForward = month < currentMonth,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )
            Spacer(Modifier.height(8.dp))
            WeekdayHeader()
            Spacer(Modifier.height(4.dp))
            view.weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectDay(cell.date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Monday-first, matching the engine's ISO weeks.
        for (i in 0..6) {
            val day = DayOfWeek.MONDAY.plus(i.toLong())
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One day. Only an in-month day that has content is interactive and dotted; padding days
 * from adjacent months render faint and blank so each month's grid only ever speaks
 * about its own days. Today gets a quiet ring — an orientation cue, not a reward.
 */
@Composable
private fun DayCell(
    cell: CalendarCell,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val selectable = cell.inMonth && cell.hasContent
    val showDot = cell.inMonth && cell.hasContent

    val numberColor = when {
        !cell.inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        cell.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(
                if (cell.isToday) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                }
            )
            .then(if (selectable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                color = numberColor,
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (showDot) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
        }
    }
}

/** Persists the visible month across config changes (YearMonth isn't natively saveable). */
private val YearMonthSaver: Saver<YearMonth, String> = Saver(
    save = { it.toString() },
    restore = { YearMonth.parse(it) },
)
