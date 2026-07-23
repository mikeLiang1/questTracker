package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.JournalEntry
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
        buildTodayBoard(quests, completions, clock.today(), clock.zone())

    /**
     * Un-clears (undoes) today's manual completion of [quest] — the same-day mis-tap
     * exception to "gains are permanent". Caller deletes the returned record via
     * [com.mikeliang.questtracker.core.repository.QuestRepository.deleteCompletion];
     * anything banked before today, or banked by health data, stays banked.
     */
    fun unclear(quest: Quest, completions: List<CompletionRecord>): UnclearOutcome =
        unclearQuest(quest, completions, clock.today(), clock.zone())

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
                    // Frozen at record time, like periodStart: later edits to the
                    // quest's attribute or cadence never rewrite banked accrual.
                    attribute = kind.attribute,
                    basePoints = AccrualRules.basePoints(kind.cadence),
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

    /**
     * The completions a just-saved journal entry banks: every active journal-linked
     * recurring quest not already done this period, each through [complete] (so
     * period dedupe and frozen accrual apply). Caller persists every record in
     * [JournalSaveResult.records]; empty records is a normal outcome, not an error.
     */
    fun completeFromJournalEntry(
        quests: List<Quest>,
        completions: List<CompletionRecord>,
    ): JournalSaveResult =
        completeJournalLinkedQuests(quests) { quest, newRecords ->
            complete(quest, quests, completions + newRecords, CompletionSource.Manual)
        }

    /** The Quest Log timeline in the user's current zone, newest first. */
    fun questLog(
        quests: List<Quest>,
        completions: List<CompletionRecord>,
        entries: List<JournalEntry>,
    ): List<QuestLogDay> = buildQuestLog(quests, completions, entries, clock.zone())

    /**
     * Applies a validated [QuestEdit] to [quest] as of today (the clock stamps
     * [Quest.cadenceChangedOn] on cadence changes). Caller persists the copy.
     */
    fun edit(quest: Quest, edit: QuestEdit, completions: List<CompletionRecord>): Quest =
        editQuest(quest, edit, completions, clock.today())

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
