package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.health.HealthReading
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Decides whether a day's health reading auto-completes [quest] for the period
 * containing [date]. This is the completion decision for background sync — :health
 * reads Health Connect and persists what comes back, but the decision itself lives
 * here, pure and testable.
 *
 * Returns the [CompletionRecord] to persist, or null when there is nothing to do.
 * Null is always neutral, never a failure: an [HealthReading.Unavailable] reading, a
 * below-target day, or an already-banked period all mean "record nothing", so a sync
 * gap can only ever fail to add — it can never take anything away.
 *
 * [date] is the local date the reading covers (the sync window may lag behind today);
 * the credited period is frozen from it at record time, per the Phase 1 time rules.
 */
fun autoCompletionFor(
    quest: Quest,
    date: LocalDate,
    reading: HealthReading,
    completions: List<CompletionRecord>,
    recordedAt: Instant,
): CompletionRecord? {
    if (quest.status != QuestStatus.Active) return null
    val kind = quest.kind as? QuestKind.Recurring ?: return null
    val tracking = quest.autoTracking ?: return null
    val value = (reading as? HealthReading.Available)?.value ?: return null
    if (value < tracking.dailyTarget) return null

    val periodStart = periodStartFor(date, kind.cadence)
    val alreadyBanked = completions.any {
        it.questId == quest.id && periodStartFor(it.periodStart, kind.cadence) == periodStart
    }
    if (alreadyBanked) return null

    return CompletionRecord(
        questId = quest.id,
        completedAt = recordedAt,
        periodStart = periodStart,
        source = CompletionSource.AutoTracked,
        escalationLevel = kind.progression?.escalationLevel,
    )
}
