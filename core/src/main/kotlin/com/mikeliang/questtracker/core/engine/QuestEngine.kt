package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import java.time.ZonedDateTime

/** Result of attempting a completion. Repeat completions are a no-op, never an error. */
sealed interface CompletionOutcome {

    /** Persist [record] and show [feedback]. */
    data class Completed(
        val record: CompletionRecord,
        val feedback: CompletionFeedback,
    ) : CompletionOutcome

    /** The current period (or the side quest itself) is already banked. */
    data object AlreadyCompleted : CompletionOutcome
}

/**
 * The clock-aware facade over the pure engine functions — the only place :app needs
 * to touch for the daily loop. Stateless: callers pass current quests/completions
 * (from [com.mikeliang.questtracker.core.repository.QuestRepository]) and persist
 * what comes back.
 */
class QuestEngine(private val clock: Clock) {

    /** Today's board in the user's current zone. */
    fun todayBoard(quests: List<Quest>, completions: List<CompletionRecord>): TodayBoard =
        buildTodayBoard(quests, completions, clock.today())

    /**
     * Completes [quest] now. Credits the period containing this moment in the user's
     * current zone (frozen into the record), dedupes against [completions], and
     * produces identity-framed feedback using [quests] for attribute context.
     */
    fun complete(
        quest: Quest,
        quests: List<Quest>,
        completions: List<CompletionRecord>,
        source: CompletionSource,
    ): CompletionOutcome {
        require(quest.status == QuestStatus.Active) {
            "Cannot complete a retired quest: ${quest.id.value}"
        }
        val now = clock.now()
        val today = now.atZone(clock.zone()).toLocalDate()
        val questRecords = completions.filter { it.questId == quest.id }

        val record = when (val kind = quest.kind) {
            is QuestKind.Recurring -> {
                val periodStart = periodStartFor(today, kind.cadence)
                val alreadyDone = questRecords.any {
                    periodStartFor(it.periodStart, kind.cadence) == periodStart
                }
                if (alreadyDone) return CompletionOutcome.AlreadyCompleted
                CompletionRecord(
                    questId = quest.id,
                    completedAt = now,
                    periodStart = periodStart,
                    source = source,
                    escalationLevel = kind.progression?.escalationLevel,
                )
            }

            QuestKind.SideQuest -> {
                if (questRecords.isNotEmpty()) return CompletionOutcome.AlreadyCompleted
                CompletionRecord(
                    questId = quest.id,
                    completedAt = now,
                    periodStart = today,
                    source = source,
                )
            }
        }

        val knownQuests = if (quests.any { it.id == quest.id }) quests else quests + quest
        return CompletionOutcome.Completed(
            record = record,
            feedback = buildCompletionFeedback(quest, knownQuests, completions, record),
        )
    }

    /** The profile screen's summary as of today. */
    fun profile(quests: List<Quest>, completions: List<CompletionRecord>): ProfileSummary =
        buildProfile(quests, completions, clock.today())

    /** Consistency for a recurring [quest] as of today. */
    fun consistency(quest: Quest, completions: List<CompletionRecord>): ConsistencyScore =
        consistencyScore(quest, completions, clock.today(), clock.zone())

    /** Next-due reminders across [quests] from this moment, soonest first. */
    fun dueReminders(quests: List<Quest>, completions: List<CompletionRecord>): List<DueReminder> =
        dueRemindersAfter(quests, completions, ZonedDateTime.ofInstant(clock.now(), clock.zone()))
}
