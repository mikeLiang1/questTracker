package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** The locked consistency window and rest-day absorption rule (build plan, Phase 1). */
object ConsistencyRules {

    /** Rolling window of fully-elapsed periods to evaluate. */
    fun windowPeriods(cadence: Cadence): Int = when (cadence) {
        Cadence.Daily -> 28
        Cadence.Weekly -> 12
        Cadence.Monthly -> 6
    }

    /**
     * Rest allowance = ⌈evaluated / divisor⌉. Ceiling, so even a brand-new quest
     * absorbs one miss — weeks 2–4 are where churn lives.
     */
    fun absorptionDivisor(cadence: Cadence): Int = when (cadence) {
        Cadence.Daily -> 7
        Cadence.Weekly -> 6
        Cadence.Monthly -> 6
    }
}

/**
 * A consistency rate — deliberately not a streak. [absorbedRestPeriods] misses were
 * neutralized as rest; only misses beyond the allowance lower [rate]. This is also
 * the sync-failure shield: the engine only sees completions, so an occasional gap
 * (missed day or missed sync alike) costs nothing.
 */
data class ConsistencyScore(
    val evaluatedPeriods: Int,
    val completedPeriods: Int,
    val absorbedRestPeriods: Int,
    val rate: Double,
)

/**
 * Scores [quest] (must be recurring) over its rolling window as of [today]. The
 * current in-progress period is excluded — today is never a miss before it ends —
 * and so are periods before the quest existed ([zone] localizes its creation
 * instant). Zero evaluated periods score a neutral 1.0.
 */
fun consistencyScore(
    quest: Quest,
    completions: List<CompletionRecord>,
    today: LocalDate,
    zone: ZoneId,
): ConsistencyScore {
    val kind = quest.kind
    require(kind is QuestKind.Recurring) { "Consistency is only defined for recurring quests" }
    val cadence = kind.cadence

    val creationStart = periodStartFor(quest.createdAt.atZone(zone).toLocalDate(), cadence)
    val completedStarts = completions
        .filter { it.questId == quest.id }
        .map { periodStartFor(it.periodStart, cadence) }
        .toSet()

    var period = periodContaining(today, cadence).previous()
    var evaluated = 0
    var completed = 0
    while (evaluated < ConsistencyRules.windowPeriods(cadence) && period.start >= creationStart) {
        evaluated++
        if (period.start in completedStarts) completed++
        period = period.previous()
    }

    val misses = evaluated - completed
    val allowance = ceil(evaluated.toDouble() / ConsistencyRules.absorptionDivisor(cadence)).toInt()
    val absorbed = min(misses, allowance)
    val rate = if (evaluated == 0) {
        1.0
    } else {
        (completed.toDouble() / max(1, evaluated - absorbed)).coerceAtMost(1.0)
    }
    return ConsistencyScore(evaluated, completed, absorbed, rate)
}
