package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind

/**
 * The locked accrual math (build plan, Phase 1 decisions). Points are cumulative and
 * permanent — nothing here ever subtracts.
 */
object AccrualRules {

    /** Completions at one escalation level earning full base before returns diminish. */
    const val FULL_RATE_COMPLETIONS_PER_LEVEL: Int = 15

    /** Multiplier once a progression level is farmed past the full-rate allowance. */
    const val DIMINISHED_MULTIPLIER: Double = 0.5

    /** Base points per completion — proportional to the commitment window. */
    fun basePoints(cadence: Cadence): Double = when (cadence) {
        Cadence.Daily -> 1.0
        Cadence.Weekly -> 3.0
        Cadence.Monthly -> 10.0
    }
}

/**
 * One attribute's accumulated evidence, with milestone context for display
 * ("Consistent — Body, 12.5 points to Established").
 */
data class AttributeProgress(
    val attribute: Attribute,
    val points: Double,
    val completions: Int,
    val rank: Int,
    val title: String,
    val nextTitle: String,
    val pointsToNextRank: Double,
)

/**
 * Folds every recurring completion into per-attribute progress. Side quests are
 * ignored by construction (the identity firewall). All four attributes are always
 * present — an untouched attribute is information, not shame.
 *
 * Each record is priced from its own frozen context ([CompletionRecord.attribute],
 * [CompletionRecord.basePoints], [CompletionRecord.escalationLevel]) so editing a
 * quest's attribute or cadence never moves or re-prices banked points. Records from
 * before freezing existed fall back to the quest's current values — total either way.
 */
fun attributeProgress(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
): Map<Attribute, AttributeProgress> {
    val byQuest = completions.groupBy { it.questId }
    val points = mutableMapOf<Attribute, Double>()
    val counts = mutableMapOf<Attribute, Int>()

    for (quest in quests) {
        val kind = quest.kind as? QuestKind.Recurring ?: continue
        // periodStart is normalized at record time, so deduping on it directly is
        // cadence-independent — a cadence edit never collapses banked credits.
        val deduped = byQuest[quest.id].orEmpty()
            .sortedBy { it.completedAt }
            .distinctBy { it.periodStart }

        val completionsAtLevel = mutableMapOf<Int, Int>()
        for (record in deduped) {
            val attribute = record.attribute ?: kind.attribute
            val base = record.basePoints ?: AccrualRules.basePoints(kind.cadence)
            // Diminishing returns key off the record's frozen level alone: a record
            // banked without one (maintenance/outcome) always earns full base, even
            // if the quest later gains a target.
            val level = record.escalationLevel
            val earned = if (level == null) {
                base
            } else {
                val nthAtLevel = completionsAtLevel.merge(level, 1, Int::plus)!!
                if (nthAtLevel <= AccrualRules.FULL_RATE_COMPLETIONS_PER_LEVEL) {
                    base
                } else {
                    base * AccrualRules.DIMINISHED_MULTIPLIER
                }
            }
            points.merge(attribute, earned, Double::plus)
            counts.merge(attribute, 1, Int::plus)
        }
    }

    return Attribute.entries.associateWith { attribute ->
        val total = points[attribute] ?: 0.0
        val rank = Milestones.rankFor(total)
        val next = Milestones.nextMilestone(total)
        AttributeProgress(
            attribute = attribute,
            points = total,
            completions = counts[attribute] ?: 0,
            rank = rank,
            title = Milestones.titleFor(rank),
            nextTitle = Milestones.titleFor(next.rank),
            pointsToNextRank = next.pointsRemaining,
        )
    }
}

