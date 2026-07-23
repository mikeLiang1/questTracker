package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import java.time.LocalDate
import java.time.ZoneId

/** Result of attempting to un-clear (undo) a completion. */
sealed interface UnclearOutcome {

    /** Delete [record] from the repository; the quest re-opens for its current period. */
    data class Uncleared(val record: CompletionRecord) : UnclearOutcome

    /**
     * Nothing to undo: no current-period completion, it was banked before today, or
     * it was auto-tracked. A normal outcome (stale UI, double taps), never an error.
     */
    data object NotUndoable : UnclearOutcome
}

/**
 * The same-day mis-tap exception to "gains are permanent" (decision recorded in the
 * build plan, 2026-07-24): a completion the user banked *by hand, today* can be
 * un-cleared until the day rolls over in their current zone. Anything older is banked
 * forever, and auto-tracked completions are never undoable — the health data that
 * banked them would immediately bank them again.
 */
fun isUndoableOn(record: CompletionRecord, today: LocalDate, zone: ZoneId): Boolean =
    record.source == CompletionSource.Manual &&
        record.completedAt.atZone(zone).toLocalDate() == today

/**
 * Finds the completion an un-clear tap should delete: the latest record credited to
 * the quest's current period that is still inside the same-day window per
 * [isUndoableOn]. Callers delete [UnclearOutcome.Uncleared.record] and nothing else.
 */
fun unclearQuest(
    quest: Quest,
    completions: List<CompletionRecord>,
    today: LocalDate,
    zone: ZoneId,
): UnclearOutcome {
    val records = completions.filter { it.questId == quest.id }
    val currentPeriod = when (val kind = quest.kind) {
        is QuestKind.Recurring -> {
            val periodStart = periodStartFor(today, kind.cadence)
            records.filter { periodStartFor(it.periodStart, kind.cadence) == periodStart }
        }

        QuestKind.SideQuest -> records
    }
    val undoable = currentPeriod
        .filter { isUndoableOn(it, today, zone) }
        .maxByOrNull { it.completedAt }
        ?: return UnclearOutcome.NotUndoable
    return UnclearOutcome.Uncleared(undoable)
}
