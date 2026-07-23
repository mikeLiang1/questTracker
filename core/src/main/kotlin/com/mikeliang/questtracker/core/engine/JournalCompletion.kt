package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import java.time.LocalDate

/**
 * What saving a journal entry banked. [records] is empty when nothing new completed
 * (no journal-linked quests, or all already banked this period) — which is fine, the
 * entry itself is the point; the UI shows a plain "saved" note, never "didn't count".
 */
data class JournalSaveResult(
    val records: List<CompletionRecord>,
    val feedback: CompletionFeedback?,
)

/**
 * The journal-linked quests a new entry could complete right now: active, linked, and
 * not yet banked for the period containing [today]. This is what the write sheet
 * shows as pre-selected options — the link made visible at the moment of writing.
 */
fun journalLinkedCandidates(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    today: LocalDate,
): List<Quest> = quests.filter { quest ->
    val kind = quest.kind as? QuestKind.Recurring ?: return@filter false
    if (quest.status != QuestStatus.Active || !kind.journalLinked) return@filter false
    val periodStart = periodStartFor(today, kind.cadence)
    completions.none {
        it.questId == quest.id && periodStartFor(it.periodStart, kind.cadence) == periodStart
    }
}

/**
 * Completes every active journal-linked recurring quest that isn't already banked for
 * its current period. Pure fold: [complete] is the engine's completion function,
 * called with the base completions plus records earned earlier in this same save, so
 * dedupe and feedback both see the full picture (one save that clears the whole board
 * produces done-for-today framing). [feedback][JournalSaveResult.feedback] is the last
 * completion's, computed with every new record visible.
 *
 * [only] narrows the fold to the quests the user left selected in the write sheet;
 * null means everything linked (the default — an untouched sheet counts toward all).
 * Selection only ever *excludes* — a quest outside the linked set can never be
 * completed this way regardless of what [only] contains.
 */
fun completeJournalLinkedQuests(
    quests: List<Quest>,
    only: Set<QuestId>? = null,
    complete: (quest: Quest, completions: List<CompletionRecord>) -> CompletionOutcome,
): JournalSaveResult {
    val linked = quests.filter {
        it.status == QuestStatus.Active &&
            (it.kind as? QuestKind.Recurring)?.journalLinked == true &&
            (only == null || it.id in only)
    }
    var records = emptyList<CompletionRecord>()
    var feedback: CompletionFeedback? = null
    for (quest in linked) {
        when (val outcome = complete(quest, records)) {
            is CompletionOutcome.Completed -> {
                records = records + outcome.record
                feedback = outcome.feedback
            }
            CompletionOutcome.AlreadyCompleted -> Unit
        }
    }
    return JournalSaveResult(records, feedback)
}
