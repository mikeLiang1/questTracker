package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import java.time.LocalDate

/** A recurring quest on today's board, with its current period's completion state. */
data class DueQuest(
    val quest: Quest,
    val period: QuestPeriod,
    val completed: Boolean,
)

/** A side quest on today's board: still open, or ticked off earlier today. */
data class SideQuestEntry(
    val quest: Quest,
    val completed: Boolean,
)

/**
 * The main screen's data. [doneForToday] is the anti-compulsion signal: true when
 * every active recurring quest is completed for its current period. Side quests
 * never block it — they're life admin with no due date, not identity work — and an
 * empty board is an empty state, not "done".
 */
data class TodayBoard(
    val recurring: List<DueQuest>,
    val sideQuests: List<SideQuestEntry>,
    val doneForToday: Boolean,
)

/**
 * Builds the board for [today]: active recurring quests ordered daily → weekly →
 * monthly, plus open side quests and side quests completed today (completed-earlier
 * side quests belong to history, not the board).
 */
fun buildTodayBoard(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    today: LocalDate,
): TodayBoard {
    val active = quests.filter { it.status == QuestStatus.Active }
    val byQuest = completions.groupBy { it.questId }

    val recurring = active
        .mapNotNull { quest ->
            val kind = quest.kind as? QuestKind.Recurring ?: return@mapNotNull null
            val period = periodContaining(today, kind.cadence)
            val completed = byQuest[quest.id].orEmpty()
                .any { periodStartFor(it.periodStart, kind.cadence) == period.start }
            DueQuest(quest, period, completed)
        }
        .sortedWith(
            compareBy(
                { (it.quest.kind as QuestKind.Recurring).cadence.ordinal },
                { it.quest.title },
            )
        )

    val sideQuests = active
        .filter { it.kind == QuestKind.SideQuest }
        .mapNotNull { quest ->
            val records = byQuest[quest.id].orEmpty()
            when {
                records.isEmpty() -> SideQuestEntry(quest, completed = false)
                records.any { it.periodStart == today } -> SideQuestEntry(quest, completed = true)
                else -> null
            }
        }
        .sortedBy { it.quest.createdAt }

    val doneForToday = recurring.isNotEmpty() && recurring.all { it.completed }
    return TodayBoard(recurring, sideQuests, doneForToday)
}
