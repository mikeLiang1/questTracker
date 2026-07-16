package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind

/**
 * Collapses multiple records in the same [cadence] period to the earliest one.
 * Call on a single quest's records — one credit per quest per period, everywhere.
 */
fun List<CompletionRecord>.dedupedByPeriod(cadence: Cadence): List<CompletionRecord> =
    sortedBy { it.completedAt }
        .distinctBy { periodStartFor(it.periodStart, cadence) }

/**
 * Total lifetime completions across all quests — the headline number, because it can
 * never break or shrink. Recurring quests credit once per period; a side quest
 * credits once, ever.
 */
fun lifetimeCompletionCount(quests: List<Quest>, completions: List<CompletionRecord>): Int {
    val byQuest = completions.groupBy { it.questId }
    return quests.sumOf { quest ->
        val records = byQuest[quest.id].orEmpty()
        when (val kind = quest.kind) {
            is QuestKind.Recurring -> records.dedupedByPeriod(kind.cadence).size
            QuestKind.SideQuest -> if (records.isEmpty()) 0 else 1
        }
    }
}
