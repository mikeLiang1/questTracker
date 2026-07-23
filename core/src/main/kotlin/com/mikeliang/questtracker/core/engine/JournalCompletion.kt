package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus

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
 * Completes every active journal-linked recurring quest that isn't already banked for
 * its current period. Pure fold: [complete] is the engine's completion function,
 * called with the base completions plus records earned earlier in this same save, so
 * dedupe and feedback both see the full picture (one save that clears the whole board
 * produces done-for-today framing). [feedback][JournalSaveResult.feedback] is the last
 * completion's, computed with every new record visible.
 */
fun completeJournalLinkedQuests(
    quests: List<Quest>,
    complete: (quest: Quest, completions: List<CompletionRecord>) -> CompletionOutcome,
): JournalSaveResult {
    val linked = quests.filter {
        it.status == QuestStatus.Active && (it.kind as? QuestKind.Recurring)?.journalLinked == true
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
