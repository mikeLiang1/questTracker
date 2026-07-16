package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind

/**
 * Identity-framed completion copy, produced by the engine so the UI never invents
 * reward language. Evidence of who the user is becoming — never "+XP", never guilt.
 * Every variant is positive; there is intentionally no failure/loss variant to build.
 */
sealed interface CompletionFeedback {

    /** Ready-to-display copy. */
    val message: String

    /** This completion crossed an attribute milestone — the strongest moment. */
    data class MilestoneReached(
        val attribute: Attribute,
        val newRank: Int,
        val newTitle: String,
        override val message: String,
    ) : CompletionFeedback

    /** ≥ 2 consecutive periods completed: "4th week running — consistent." */
    data class ConsecutiveRun(
        val runLength: Int,
        val cadence: Cadence,
        override val message: String,
    ) : CompletionFeedback

    /** Recurring fallback: the lifetime count, which only ever grows. */
    data class EvidenceBanked(
        val lifetimeCompletions: Int,
        override val message: String,
    ) : CompletionFeedback

    /** Side quests get the tick and the lifetime tally — never attribute framing. */
    data class SideQuestCleared(
        val lifetimeSideQuests: Int,
        override val message: String,
    ) : CompletionFeedback
}

/**
 * Feedback for [record], freshly earned on [quest]. [priorCompletions] excludes the
 * new record; [quests] provides the attribute-wide context for milestone detection.
 * Priority: milestone > consecutive run (≥ 2) > lifetime evidence.
 */
fun buildCompletionFeedback(
    quest: Quest,
    quests: List<Quest>,
    priorCompletions: List<CompletionRecord>,
    record: CompletionRecord,
): CompletionFeedback = when (val kind = quest.kind) {
    QuestKind.SideQuest -> {
        val sideQuestIds = quests.filter { it.kind == QuestKind.SideQuest }.map { it.id }.toSet()
        val cleared = (priorCompletions + record)
            .filter { it.questId in sideQuestIds }
            .map { it.questId }
            .distinct()
            .count()
        CompletionFeedback.SideQuestCleared(
            lifetimeSideQuests = cleared,
            message = "Side quest cleared — ${cleared.toOrdinal()} lifetime.",
        )
    }

    is QuestKind.Recurring -> {
        val attribute = kind.attribute
        val rankBefore = attributeProgress(quests, priorCompletions).getValue(attribute).rank
        val after = attributeProgress(quests, priorCompletions + record).getValue(attribute)

        val questRecords = priorCompletions.filter { it.questId == quest.id } + record
        val completedStarts = questRecords.map { periodStartFor(it.periodStart, kind.cadence) }.toSet()
        var run = 1
        var period = periodContaining(record.periodStart, kind.cadence).previous()
        while (period.start in completedStarts) {
            run++
            period = period.previous()
        }

        when {
            after.rank > rankBefore -> CompletionFeedback.MilestoneReached(
                attribute = attribute,
                newRank = after.rank,
                newTitle = after.title,
                message = "New title: ${after.title} — $attribute.",
            )

            run >= 2 -> CompletionFeedback.ConsecutiveRun(
                runLength = run,
                cadence = kind.cadence,
                message = "${run.toOrdinal()} ${kind.cadence.unitName()} running — consistent.",
            )

            else -> {
                val lifetime = questRecords.dedupedByPeriod(kind.cadence).size
                CompletionFeedback.EvidenceBanked(
                    lifetimeCompletions = lifetime,
                    message = "Completion #$lifetime — banked for good.",
                )
            }
        }
    }
}

private fun Cadence.unitName(): String = when (this) {
    Cadence.Daily -> "day"
    Cadence.Weekly -> "week"
    Cadence.Monthly -> "month"
}

internal fun Int.toOrdinal(): String {
    val suffix = when {
        this % 100 in 11..13 -> "th"
        this % 10 == 1 -> "st"
        this % 10 == 2 -> "nd"
        this % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$this$suffix"
}
