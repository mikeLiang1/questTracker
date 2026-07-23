package com.mikeliang.questtracker.reminders

import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.CompletionOutcome
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.nextReminderAfter
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.CompletionSource
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.repository.QuestRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Keeps the OS alarm set for each quest in agreement with :core's pure next-due
 * computation. This is where the no-nag law is enforced against the OS: a quest is
 * only ever scheduled for the exact instant [nextReminderAfter] returns (which already
 * skips completed periods and retired quests), and any quest that shouldn't fire has
 * its alarm cancelled. All Android specifics live behind [AlarmScheduler], so this
 * class is a plain unit test.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val repository: QuestRepository,
    private val alarmScheduler: AlarmScheduler,
    private val engine: QuestEngine,
    private val clock: Clock,
) {

    /**
     * Collect forever, re-syncing every quest's alarm whenever quests or completions
     * change. Completing a quest therefore cancels (or advances) its reminder for free,
     * and adding/editing one schedules it — no ViewModel plumbing required. Started
     * once from the Application.
     */
    suspend fun keepInSync(): Nothing {
        combine(
            repository.observeQuests(),
            repository.observeCompletions(),
        ) { quests, completions -> quests to completions }
            .collect { (quests, completions) -> syncAll(quests, completions) }
        error("keepInSync collected to completion, but the source flows never terminate")
    }

    /** One-shot resync from current repository state — used on boot and time changes. */
    suspend fun rescheduleAll() {
        val quests = repository.observeQuests().first()
        val completions = repository.observeCompletions().first()
        syncAll(quests, completions)
    }

    /** Recompute just [questId]'s alarm — used after an alarm fires. */
    suspend fun rescheduleOne(questId: QuestId) {
        val quest = repository.getQuest(questId)
        if (quest == null) {
            alarmScheduler.cancel(questId)
            return
        }
        sync(quest, repository.completionsFor(questId))
    }

    /**
     * The notification "Complete" action. Banks a manual completion through the same
     * engine path the daily-loop UI uses (manual is never second-class), then advances
     * the alarm — which, for a now-completed period, becomes a cancel.
     */
    suspend fun completeFromNotification(questId: QuestId) {
        val quest = repository.getQuest(questId) ?: return
        val quests = repository.observeQuests().first()
        val completions = repository.observeCompletions().first()
        when (val outcome = engine.complete(quest, quests, completions, CompletionSource.Manual)) {
            is CompletionOutcome.Completed -> repository.recordCompletion(outcome.record)
            CompletionOutcome.AlreadyCompleted -> Unit
        }
        rescheduleOne(questId)
    }

    /**
     * Ids seen on the previous sync. A quest that has since vanished (hard-deleted
     * mis-creation, Phase 6b) is no longer iterated by [syncAll], so its alarm must
     * be cancelled explicitly — otherwise the OS would fire for a quest that no
     * longer exists, the one nag the no-nag law can't tolerate.
     */
    private var lastSyncedIds: Set<QuestId> = emptySet()

    private fun syncAll(quests: List<Quest>, completions: List<CompletionRecord>) {
        quests.forEach { quest -> sync(quest, completions) }
        val currentIds = quests.map { it.id }.toSet()
        (lastSyncedIds - currentIds).forEach { alarmScheduler.cancel(it) }
        lastSyncedIds = currentIds
    }

    private fun sync(quest: Quest, completions: List<CompletionRecord>) {
        val from = clock.now().atZone(clock.zone())
        val at = nextReminderAfter(quest, completions, from)
        if (at != null) alarmScheduler.schedule(quest.id, at) else alarmScheduler.cancel(quest.id)
    }
}
