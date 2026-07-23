package com.mikeliang.questtracker.ui.questdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikeliang.questtracker.core.Clock
import com.mikeliang.questtracker.core.engine.QuestEdit
import com.mikeliang.questtracker.core.engine.QuestEngine
import com.mikeliang.questtracker.core.engine.canDeleteQuest
import com.mikeliang.questtracker.core.engine.escalate
import com.mikeliang.questtracker.core.engine.journalEntriesFor
import com.mikeliang.questtracker.core.engine.lifetimeCompletionCount
import com.mikeliang.questtracker.core.engine.retireQuest
import com.mikeliang.questtracker.core.model.JournalEntryId
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestId
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import com.mikeliang.questtracker.core.model.ReminderSchedule
import com.mikeliang.questtracker.core.repository.JournalRepository
import com.mikeliang.questtracker.core.repository.QuestRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One quest's detail. Deliberately thin, like every ViewModel here: validation and
 * the forward-only edit rules live in :core ([QuestEdit], `editQuest`, `escalate`,
 * `retireQuest`, `canDeleteQuest`) — this class snapshots repository state, calls
 * :core, and persists the copy that comes back. Reminders need no plumbing at all:
 * the Application-scoped ReminderCoordinator re-syncs alarms on any repository change.
 */
@HiltViewModel(assistedFactory = QuestDetailViewModel.Factory::class)
class QuestDetailViewModel @AssistedInject constructor(
    // The raw id string, not QuestId: @JvmInline value classes mangle the factory
    // method name, which Dagger's @AssistedFactory can't process.
    @Assisted questIdValue: String,
    private val repository: QuestRepository,
    private val journalRepository: JournalRepository,
    private val engine: QuestEngine,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(questIdValue: String): QuestDetailViewModel
    }

    private val questId = QuestId(questIdValue)

    private val closed = MutableStateFlow(false)

    val uiState: StateFlow<QuestDetailUiState> = combine(
        repository.observeQuests(),
        repository.observeCompletions(),
        journalRepository.observeEntries(),
        closed,
    ) { quests, completions, entries, isClosed ->
        val quest = quests.firstOrNull { it.id == questId }
        if (quest == null) {
            // Deleted (or never existed): nothing to show, ask the host to pop back.
            QuestDetailUiState(closed = true)
        } else {
            val records = completions.filter { it.questId == questId }
            QuestDetailUiState(
                quest = quest,
                lifetimeCompletions = lifetimeCompletionCount(listOf(quest), records),
                consistency = if (quest.kind is QuestKind.Recurring) {
                    engine.consistency(quest, records)
                } else {
                    null
                },
                canDelete = quest.status == QuestStatus.Active && canDeleteQuest(quest, records),
                closed = isClosed,
                journalEntries = journalEntriesFor(questId, entries),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuestDetailUiState(loading = true),
    )

    fun onEvent(event: QuestDetailEvent) {
        when (event) {
            is QuestDetailEvent.SaveRecurringEdit -> saveEdit(event.edit)
            is QuestDetailEvent.SaveSideQuestEdit -> saveSideQuestEdit(event)
            is QuestDetailEvent.Escalate -> escalateTo(event.newAmount)
            QuestDetailEvent.Retire -> retire()
            QuestDetailEvent.Delete -> delete()
            is QuestDetailEvent.EditJournalEntry -> editJournalEntry(event.id, event.text)
            is QuestDetailEvent.DeleteJournalEntry -> deleteJournalEntry(event.id)
        }
    }

    private fun editJournalEntry(id: JournalEntryId, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = journalRepository.getEntry(id) ?: return@launch
            journalRepository.upsertEntry(existing.copy(text = trimmed, editedAt = clock.now()))
        }
    }

    /** Removes the entry only — the completion it counted toward stays banked. */
    private fun deleteJournalEntry(id: JournalEntryId) {
        viewModelScope.launch {
            journalRepository.deleteEntry(id)
        }
    }

    private fun saveEdit(edit: QuestEdit) {
        viewModelScope.launch {
            val quest = repository.getQuest(questId) ?: return@launch
            val completions = repository.completionsFor(questId)
            repository.upsertQuest(engine.edit(quest, edit, completions))
        }
    }

    private fun saveSideQuestEdit(event: QuestDetailEvent.SaveSideQuestEdit) {
        viewModelScope.launch {
            val quest = repository.getQuest(questId) ?: return@launch
            val reminder = sideQuestReminder(quest, event.reminderTime)
            val completions = repository.completionsFor(questId)
            repository.upsertQuest(
                engine.edit(quest, QuestEdit.EditSideQuest(event.title, reminder), completions)
            )
        }
    }

    /**
     * An unchanged time keeps the existing one-shot (saving an untouched sheet never
     * resets a pending reminder); a new time means "next time it's this o'clock" —
     * the same rule quick-add uses.
     */
    private fun sideQuestReminder(quest: Quest, time: LocalTime?): ReminderSchedule? {
        if (time == null) return null
        val existing = quest.reminder as? ReminderSchedule.OneShot
        if (existing?.at?.toLocalTime() == time) return existing
        return ReminderSchedule.OneShot(at = nextOccurrenceOf(time))
    }

    private fun nextOccurrenceOf(time: LocalTime): LocalDateTime {
        val now = LocalDateTime.ofInstant(clock.now(), clock.zone())
        val todayAt = now.toLocalDate().atTime(time)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    private fun escalateTo(newAmount: Double) {
        viewModelScope.launch {
            val quest = repository.getQuest(questId) ?: return@launch
            repository.upsertQuest(escalate(quest, newAmount))
        }
    }

    private fun retire() {
        viewModelScope.launch {
            val quest = repository.getQuest(questId) ?: return@launch
            repository.upsertQuest(retireQuest(quest))
            closed.value = true
        }
    }

    private fun delete() {
        viewModelScope.launch {
            val quest = repository.getQuest(questId) ?: return@launch
            // Re-check against fresh records: a completion may have raced in (e.g.
            // from the notification action) since the button was rendered. History
            // always wins — the quest simply stays.
            if (!canDeleteQuest(quest, repository.completionsFor(questId))) return@launch
            repository.deleteQuest(questId)
            closed.value = true
        }
    }
}
