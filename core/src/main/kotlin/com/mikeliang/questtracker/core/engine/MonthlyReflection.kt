package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One quest's month in review — the evidence a reflection shows before asking its
 * one question. Numbers only; the framing (never guilt) is the UI's job.
 */
data class QuestTrajectory(
    val quest: Quest,
    /** Credited periods whose completion happened inside the month (deduped, like accrual). */
    val completionsInMonth: Int,
    /** The quest's rolling consistency as it stood at the end of the month. */
    val consistency: ConsistencyScore,
    /** True when the quest's target changed during the month (read from frozen record levels). */
    val escalatedInMonth: Boolean,
)

/**
 * The trajectory summary for the monthly reflection (design foundation §6): what the
 * last calendar month actually looked like, per active quest and per attribute.
 */
data class ReflectionSummary(
    /** The reviewed month — always the calendar month before the one containing today. */
    val month: QuestPeriod,
    /** Active recurring quests that existed during the month, in repository order. */
    val quests: List<QuestTrajectory>,
    /** Attribute points earned inside the month, priced exactly as accrual priced them. */
    val attributeGains: Map<Attribute, Double>,
    /**
     * The quietest quest — lowest consistency, ties broken by fewest completions.
     * Null when there are fewer than two quests to compare or nothing fell short:
     * a lone quest (or a clean sweep) never gets a callout.
     */
    val barelyMovedQuestId: QuestId?,
)

/** Builds the reflection summary for the calendar month before [today]. */
fun buildReflectionSummary(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    today: LocalDate,
    zone: ZoneId,
): ReflectionSummary {
    val month = periodContaining(today, Cadence.Monthly).previous()

    fun CompletionRecord.completedOn(): LocalDate = completedAt.atZone(zone).toLocalDate()
    fun CompletionRecord.inMonth(): Boolean = completedOn() in month

    val trajectories = quests
        .filter { it.status == QuestStatus.Active && it.kind is QuestKind.Recurring }
        // Quests born after the month ended have no month to review.
        .filter { it.createdAt.atZone(zone).toLocalDate() <= month.endInclusive }
        .map { quest ->
            val records = completions
                .filter { it.questId == quest.id }
                .sortedBy { it.completedAt }
            QuestTrajectory(
                quest = quest,
                completionsInMonth = records.filter { it.inMonth() }
                    .distinctBy { it.periodStart }
                    .size,
                // Evaluated as of the month's last day — the reflection reads the month
                // back, so the score must not leak days from the month in progress.
                consistency = consistencyScore(
                    quest, completions, month.endInclusive.plusDays(1), zone,
                ),
                escalatedInMonth = escalatedWithin(records) { it.inMonth() },
            )
        }

    val barelyMoved = if (trajectories.size < 2) {
        null
    } else {
        trajectories
            .filter { it.consistency.rate < 1.0 }
            .minWithOrNull(compareBy({ it.consistency.rate }, { it.completionsInMonth }))
            ?.quest?.id
    }

    return ReflectionSummary(
        month = month,
        quests = trajectories,
        attributeGains = attributeGainsWithin(quests, completions) { it.inMonth() },
        barelyMovedQuestId = barelyMoved,
    )
}

/**
 * Whether the reflection should be surfaced: some completion history predates the
 * current month, and this month hasn't been handled (completed *or* skipped) yet.
 * Never forced — this only gates a banner, and a skip re-arms it next month.
 */
fun isReflectionDue(
    completions: List<CompletionRecord>,
    today: LocalDate,
    lastHandledMonth: YearMonth?,
): Boolean {
    val currentMonth = YearMonth.from(today)
    if (lastHandledMonth != null && lastHandledMonth >= currentMonth) return false
    return completions.any { it.periodStart < currentMonth.atDay(1) }
}

/**
 * Escalation during the window, derived purely from the frozen levels on records:
 * either the window itself holds more than one level, or its levels rose past the
 * last level banked before it. A target change with no completions after it stays
 * invisible — records are the only evidence this module trusts.
 */
private fun escalatedWithin(
    sortedRecords: List<CompletionRecord>,
    inWindow: (CompletionRecord) -> Boolean,
): Boolean {
    val windowLevels = sortedRecords.filter(inWindow).mapNotNull { it.escalationLevel }
    if (windowLevels.isEmpty()) return false
    if (windowLevels.distinct().size > 1) return true
    val priorLevel = sortedRecords
        .takeWhile { !inWindow(it) }
        .lastOrNull { it.escalationLevel != null }
        ?.escalationLevel
    return priorLevel != null && windowLevels.max() > priorLevel
}

/**
 * Points earned inside the window, per attribute. Replays the same fold as
 * [attributeProgress] over the full history — diminishing returns depend on every
 * completion ever farmed at a level — but only sums records the window contains,
 * so the month's gains match what accrual actually banked for them. Retired quests
 * participate: their in-window records were earned regardless of what came later.
 */
private fun attributeGainsWithin(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    inWindow: (CompletionRecord) -> Boolean,
): Map<Attribute, Double> {
    val byQuest = completions.groupBy { it.questId }
    val gains = mutableMapOf<Attribute, Double>()

    for (quest in quests) {
        val kind = quest.kind as? QuestKind.Recurring ?: continue
        val deduped = byQuest[quest.id].orEmpty()
            .sortedBy { it.completedAt }
            .distinctBy { it.periodStart }

        val completionsAtLevel = mutableMapOf<Int, Int>()
        for (record in deduped) {
            val base = record.basePoints ?: AccrualRules.basePoints(kind.cadence)
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
            if (inWindow(record)) {
                gains.merge(record.attribute ?: kind.attribute, earned, Double::plus)
            }
        }
    }

    return Attribute.entries.associateWith { gains[it] ?: 0.0 }
}
